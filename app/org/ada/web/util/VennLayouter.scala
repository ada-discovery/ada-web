package org.ada.web.util

import org.ada.server.models.Dictionary

package object VennLayouter {

  /**
    * Recalculate positions and radii of given elements for layouting purposes.
    * Arranges venn diagram elements in circular fashion to fit inside the box defined by sizeX and sizeY.
    *
    * @param elements original input element sequence.
    * @param sizeX optional parameter specifying the width.
    * @param sizeY optional parameter specifying the height.
    * @return sequence of values aligned in circle around center of specified box.
    *
    * */
  def calculateCoordinates(elements: Seq[Dictionary], sizeX: Int = 100, sizeY: Int = 100): Seq[(String, Int, Int, Int)] = {
    val offset = math.Pi / 2
    val vennSize = (sizeX + sizeY) / 2
    val scale = vennSize / 10
    val num = elements.length
    val factor = (2.0 * math.Pi) / num


    // replace with folding
    var maxRadius = 0;
    for (e <- elements) {
      maxRadius = math.max(maxRadius, e.fields.length)
    }

    //center points
    //replace with final value
    val cx = sizeX / 2
    val cy = sizeY / 2
    var i = 0

    // define function for value mapping
    def f(x: Dictionary) = {
      val px = math.cos(offset + (factor * i).toInt)
      val py = math.sin(offset + (factor * i).toInt)
      i += 1
      (x.dataSetId, /*(x.fields.length * vennSize) / maxRadius).toInt*/100, (cx + scale * px).toInt, (cy + scale * py).toInt)
    }
    return elements.map(f)
  }
}
