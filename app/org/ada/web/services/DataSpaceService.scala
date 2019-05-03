package org.ada.web.services

import javax.inject.{Inject, Named, Singleton}
import com.google.inject.ImplementedBy
import org.ada.server.dataaccess.DataSetMetaInfoRepoFactory
import org.ada.server.dataaccess.RepoTypes.{DataSetMetaInfoRepo, DataSpaceMetaInfoRepo}
import org.ada.server.models.{DataSpaceMetaInfo, User}
import org.ada.server.services.UserManager
import org.ada.web.security.AdaAuthConfig
import play.api.mvc.Request
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.security.SecurityRole
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[DataSpaceServiceImpl])
trait DataSpaceService {

  def allAsTree: Future[Traversable[DataSpaceMetaInfo]]

  def getTreeForCurrentUser(
    request: Request[_]
  ): Future[Traversable[DataSpaceMetaInfo]]

  def getTreeForUser(
     user: User
  ): Future[Traversable[DataSpaceMetaInfo]]

  def getDataSpaceForCurrentUser(
    dataSpace: DataSpaceMetaInfo)(
    request: Request[_]
  ): Future[Option[DataSpaceMetaInfo]]

  def unregister(
    dataSpaceInfo: DataSpaceMetaInfo,
    dataSetId: String
  ): Future[Unit]

  def findRecursively(
    id: BSONObjectID,
    root: DataSpaceMetaInfo
  ): Option[DataSpaceMetaInfo]

  def countDataSetsNumRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Int

  def countDataSpacesNumRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Int
}

@Singleton
class DataSpaceServiceImpl @Inject() (
    val userManager: UserManager,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory
  ) extends DataSpaceService with AdaAuthConfig {

  private val r1 = """^[DS:](.*[.].*)""".r
  private val r2 = """^[DS:](.*[.].*[.])""".r
  private val r3 = """^[DS:](.*[.].*[.].*[.])""".r

  override def getTreeForCurrentUser(request: Request[_]) =
    for {
      currentUser <- currentUser(request)
      dataSpaces <- currentUser match {
        case None => Future(Traversable[DataSpaceMetaInfo]())
        case Some(user) => getTreeForUser(user)
      }
    } yield
      dataSpaces

  override def getTreeForUser(user: User) =
    for {
      dataSpaces <- {
        val isAdmin = user.roles.contains(SecurityRole.admin)

        def allAsTreeAux = allAsTree.map(_.filterNot(_.parentId.isDefined))

        if (isAdmin)
          allAsTreeAux
        else {
          val dataSetIds = getUsersDataSetIds(user)
          allAsTreeAux.map(_.map(filterRecursively(dataSetIds)).flatten)
        }
      }
    } yield
      dataSpaces

  override def getDataSpaceForCurrentUser(
    dataSpace: DataSpaceMetaInfo)(
    request: Request[_]
  ): Future[Option[DataSpaceMetaInfo]] =
    for {
      currentUser <- currentUser(request)
      foundChildren <- dataSpaceMetaInfoRepo.find(Seq("parentId" #== dataSpace._id))
    } yield
      currentUser.map { user =>
        dataSpace.children.clear()
        dataSpace.children.appendAll(foundChildren)
        val isAdmin = user.roles.contains(SecurityRole.admin)
        if (isAdmin)
          Some(dataSpace)
        else {
          val dataSetIds = getUsersDataSetIds(user)
          filterRecursively(dataSetIds)(dataSpace)
        }
      }.flatten

  private def getUsersDataSetIds(user: User) =
    user.permissions.map { permission =>
      val dotsCount = permission.count(_ == '.')
      if (permission.startsWith("DS:") && dotsCount > 0) {
        val output = if (dotsCount == 1) {
          permission
        } else {
          val parts = permission.split('.')
          parts(0) + "." + parts(1)
        }
        Some(output.substring(3))
      } else
        None
    }.flatten.toSet

  override def allAsTree: Future[Traversable[DataSpaceMetaInfo]] = {
    dataSpaceMetaInfoRepo.find().map { dataSpaces =>
      val idDataSpaceMap = dataSpaces.map( dataSpace => (dataSpace._id.get, dataSpace)).toMap
      dataSpaces.foreach { dataSpace =>
        val parent = dataSpace.parentId.map(idDataSpaceMap.get).flatten
        if (parent.isDefined) {
          parent.get.children.append(dataSpace)
        }
      }
      dataSpaces
    }
  }

  override def countDataSetsNumRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Int =
    dataSpace.children.foldLeft(dataSpace.dataSetMetaInfos.size) {
      case (count, dataSpace) => count + countDataSetsNumRecursively(dataSpace)
    }

  override def countDataSpacesNumRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Int =
    dataSpace.children.foldLeft(1) {
      case (count, dataSpace) => count + countDataSpacesNumRecursively(dataSpace)
    }

  private def filterRecursively(
    acceptedDataSetIds: Set[String])(
    dataSpace: DataSpaceMetaInfo
  ): Option[DataSpaceMetaInfo] = {
    val newDataSetMetaInfos = dataSpace.dataSetMetaInfos.filter(
      info => acceptedDataSetIds.contains(info.id)
    )

    val newChildren = dataSpace.children.map(filterRecursively(acceptedDataSetIds)).flatten

    if (newDataSetMetaInfos.nonEmpty || newChildren.nonEmpty)
      Some(dataSpace.copy(dataSetMetaInfos = newDataSetMetaInfos, children = newChildren))
    else
      None
  }

  override def findRecursively(
    id: BSONObjectID,
    root: DataSpaceMetaInfo
  ): Option[DataSpaceMetaInfo] =
    if (root._id.isDefined && root._id.get.equals(id))
      Some(root)
    else
      root.children.map(findRecursively(id, _)).find(_.isDefined).flatten

  override def unregister(
    dataSpaceInfo: DataSpaceMetaInfo,
    dataSetId: String
  ) =
    for {
      // remove a data set from the data space
      _ <- {
        val filteredDataSetInfos = dataSpaceInfo.dataSetMetaInfos.filterNot(_.id.equals(dataSetId))
        dataSpaceMetaInfoRepo.update(dataSpaceInfo.copy(dataSetMetaInfos = filteredDataSetInfos))
      }

      // remove a data set from the data set meta info repo
      _ <- {
        dataSpaceInfo.dataSetMetaInfos.find(_.id.equals(dataSetId)).map { dataSetInfoToRemove =>
          val dataSetMetaInfoRepo = dataSetMetaInfoRepoFactory(dataSpaceInfo._id.get)
          dataSetMetaInfoRepo.delete(dataSetInfoToRemove._id.get)
        }.getOrElse(
          // should never happen
          Future(())
        )
      }
    } yield
      ()
}
