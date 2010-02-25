package org.squeryl.tests

import org.squeryl.KeyedEntity
import org.squeryl.annotations.{Row, Column}
import org.squeryl.Schema

@Row("T_TOASTER")
class Toaster(

  @Column(optionType=classOf[Int])
  var yearOfManufacture: Option[Int],

// TODO: uncomment when scalac bug #3003 is resolved
//  @Column(optionType=classOf[String], length=25)
//  var countryOfOrigin: Option[String],

//  @Column(name="dateOfPurchase", optionType=classOf[java.util.Date])
//  var dateOfPurchase: Option[java.util.Date]

  @Column(length=25)
  var countryOfOrigin: String,

  @Column(name="BRAND_NAME", length=32)
  var brandName: String) {

  @Column(name="WEIGHT", optionType=classOf[Float])
  var weightInGrams: Option[String] = None
}

class NailCutter extends KeyedEntity[Long] {

  val id: Long = 0
}


class KeyedObject extends KeyedEntity[Long] {
  val id: Long = 0
}

class DescendantOfKeyedObject extends KeyedObject {
  //val pouf = "pouf"
}

class AnnotationTests {


  class C(
    @Column(optionType=classOf[Long]) var j: Option[Long],
    @Column(optionType=classOf[java.lang.String]) var k: Option[String]) (
  
    @Column(optionType=classOf[Int])
    var i:Option[Int]
  )

  def allTests = {
    //rudimentaryTests
  }

  class ToastersInc extends Schema {

    val descendantOfKeyedObjects = table[DescendantOfKeyedObject]
    
    val nailCutters = table[NailCutter]

    val toasters = table[Toaster]
  }

  def testMetaData = {

    scalaReflectionTests
    
    val ti = new ToastersInc
    import ti._

    if(descendantOfKeyedObjects.findFieldMetaDataForProperty("id") == None)
      error("PosoMetaData has failed to build immutable field 'id'.")

    if(nailCutters.findFieldMetaDataForProperty("id") == None)
      error("PosoMetaData has failed to build immutable field 'id'.")
    
    val brandNameMD = toasters.findFieldMetaDataForProperty("brandName").get
    assert(brandNameMD.columnName == "BRAND_NAME", "expected 'BRAND_NAME' got " + brandNameMD.columnName)
    assert(brandNameMD.length == 32, "expected 32 got " + brandNameMD.length)

    val yearOfManufacture = toasters.findFieldMetaDataForProperty("yearOfManufacture").get
    assert(yearOfManufacture.columnName == "yearOfManufacture", "expected 'yearOfManufacture' got " + yearOfManufacture.columnName)
    assert(yearOfManufacture.length == 4, "expected 4 got " + yearOfManufacture.length)

// TODO: uncomment when scalac bug #3003 is resolved    
//    val dateOfPurchase = toasters.findFieldMetaDataForProperty("dateOfPurchase").get
//    assert(dateOfPurchase.columnName == "dateOfPurchase", "expected 'dateOfPurchase' got " + dateOfPurchase.columnName)
//    assert(dateOfPurchase.length == -1, "expected -1 got " + dateOfPurchase.length)

    println('testMetaData + " passed.")
  }

  /**
   * There has been a Scala bug with obtaining a Class[_] member in annotations,
   * if this test fails, it means that Scala has regressed TODO: file a bug 
   */
  def scalaReflectionTests = {
    val colAnotations =
      classOf[C].getDeclaredFields.toList.sortBy(f => f.getName).map(f => f.getAnnotations.toList).flatten

    val t1 = colAnotations.apply(0).asInstanceOf[Column].optionType
    val t2 = colAnotations.apply(1).asInstanceOf[Column].optionType
    val t3 = colAnotations.apply(2).asInstanceOf[Column].optionType

    assert(classOf[Int].isAssignableFrom(t1), "expected classOf[Int], got " + t1.getName)
    assert(classOf[Long].isAssignableFrom(t2), "expected classOf[Long], got " + t2.getName)
    assert(classOf[String].isAssignableFrom(t3), "expected classOf[String], got " + t3.getName)
  }  
}