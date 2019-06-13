package org.ada.web.controllers

import com.google.inject.assistedinject.FactoryModuleBuilder
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.models.dataimport.DataSetImport
import org.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation}
import org.ada.server.services.{LookupCentralExec, StaticLookupCentral, StaticLookupCentralImpl}
import org.ada.web.controllers.dataset._
import org.ada.web.controllers.dataset.dataimport.DataSetImportFormViews
import org.ada.web.controllers.dataset.datatrans.{DataSetMetaTransformationFormViews, DataSetTransformationFormViews}

class ControllerModule extends ScalaModule {

  override def configure() {

    install(new FactoryModuleBuilder()
      .implement(classOf[DataSetController], classOf[DataSetControllerImpl])
      .build(classOf[GenericDataSetControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DictionaryController], classOf[DictionaryControllerImpl])
        .build(classOf[DictionaryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[CategoryController], classOf[CategoryControllerImpl])
      .build(classOf[CategoryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[FilterController], classOf[FilterControllerImpl])
      .build(classOf[FilterControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DataViewController], classOf[DataViewControllerImpl])
      .build(classOf[DataViewControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[StandardClassificationRunController], classOf[StandardClassificationRunControllerImpl])
      .build(classOf[StandardClassificationRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[TemporalClassificationRunController], classOf[TemporalClassificationRunControllerImpl])
      .build(classOf[TemporalClassificationRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[StandardRegressionRunController], classOf[StandardRegressionRunControllerImpl])
      .build(classOf[StandardRegressionRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[TemporalRegressionRunController], classOf[TemporalRegressionRunControllerImpl])
      .build(classOf[TemporalRegressionRunControllerFactory]))

    bind[StaticLookupCentral[DataSetImportFormViews[DataSetImport]]].toInstance(
      new StaticLookupCentralImpl[DataSetImportFormViews[DataSetImport]]("org.ada.web.controllers.dataset.dataimport")
    )

    bind[StaticLookupCentral[DataSetMetaTransformationFormViews[DataSetMetaTransformation]]].toInstance(
      new StaticLookupCentralImpl[DataSetMetaTransformationFormViews[DataSetMetaTransformation]]("org.ada.web.controllers.dataset.datatrans")
    )
  }
}