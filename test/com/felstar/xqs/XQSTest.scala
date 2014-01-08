package com.felstar.xqs

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import xml.Attribute
import xml.Elem
import com.felstar.xqs.XQS._
import com.felstar.xqs.XQS.AllImplicits._
import javax.xml.xquery.XQResultSequence
import javax.xml.xquery.XQException
import scala.xml.Text
import scala.xml.Null
import org.scalatest.BeforeAndAfterAll


class XQSTest extends FunSpec with ShouldMatchers with BeforeAndAfterAll{

  private def trimShouldBe(actual: Seq[xml.Node], expected: Seq[xml.Node]):Unit={
     actual.size shouldBe expected.size   
     (actual zip expected).foreach{	 	   
       case (v,exp)=>trimShouldBe(v,exp)	       	   	  	
     }
  }
  
  private def trimShouldBe(actual: xml.Node, expected: xml.Node) {
    def recurse(actual: xml.Node, expected: xml.Node) {
        for ((actualChild, expectedChild) <- actual.child zip expected.child) {
            recurse(actualChild, expectedChild)
        }
        withClue(expected.getClass+" vs "+actual.getClass){
          // serialize both to string, makes Text==Atom etc. 
         expected.text shouldBe actual.text     
        }        
    }
    recurse(scala.xml.Utility.trim(actual), scala.xml.Utility.trim(expected))
}
  // using embedded basex 
  
 val conn =  new net.xqj.basex.local.BaseXXQDataSource().getConnection
  
 describe("ResultSequence") {
    it ("should close after use") {
      {       
      val rs=conn("1 to 4")
      val strings:Seq[String]=rs     
      rs.isClosed shouldBe true       
     }
     {      
      val rs=conn("3 to 8")
      val ints:Seq[Int]=rs     
      rs.isClosed shouldBe true
     }
     {
      val rs=conn("""1.0,2.0,3.1415926536,xs:double(123.2+100.0),xs:integer(12),
				xs:byte(120),xs:long(11111),xs:short(44),xs:int(-5),xs:negativeInteger(-44),
				xs:nonNegativeInteger(45)""")
								
      val decimals:Seq[BigDecimal]=rs     
      rs.isClosed shouldBe true
     }     
     {
       val rs=conn("""(1 to  5,44.444,<thing>{10 to 12}</thing>,
		    'xxx',<root attr='hello'>somet<mixed>MIX</mixed>hing</root>,
		    <thing attr='alone'/>/@*)""")
       val refs = toSeqAnyRef(rs)
		refs.foreach(_ match {
		  case _ => Unit
		})
	   rs.isClosed shouldBe true
     }
     {
       val rs=conn("./root()//teddy",
		 <root><child name="Timmy"><teddy><his-teddy>Little Ted</his-teddy>Mr Ted</teddy>
			<teddy><his-teddy>Little Ben</his-teddy>Mr Benson</teddy></child></root>)
			
		val elems:Seq[scala.xml.Elem]=rs
		rs.isClosed shouldBe true
     }
    }
 }
 
 describe("Expression") {
   it ("should close after use") {
      val rs=conn("1 to 4")
    
    val strings:Seq[String]=rs 
  
    getExpressionMap shouldBe empty
   }
    it ("should close after use, even with a thrown exception"){
    {
     info ("malformed decimals") 
     val rs=conn("1.0,2.1,'cat'")
     val thrown=intercept[NumberFormatException]
     {
      val decimals:Seq[BigDecimal]=rs     
     }      
     getExpressionMap shouldBe empty
    }
    {
     info ("malformed ints")  
     val rs=conn("1,2,3.1")
     val thrown=intercept[XQException]
     {
      val ints:Seq[Int]=rs     
     }         
     getExpressionMap shouldBe empty
    }
    {
     info ("malformed XML")  
     val rs=conn("<root>xxx</root>,'now fail'")
     val thrown=intercept[Exception]
     {
      val xml:Seq[Elem]=rs     
     }         
     getExpressionMap shouldBe empty
    }
   }
 }
 
 describe("Connection") {
  
  it ("can execute query on range of ints returning same as string") {    
    val (head,last)=(1,4)    
    val nums:Seq[String]=conn(s"$head to $last")        
    nums shouldBe (head to last map(_.toString))
  }
  it ("can execute query on range of ints returning same as ints") {    
    val (head,last)=(3,8)    
    val nums:Seq[Int]=conn(s"$head to $last")        
    nums shouldBe (head to last)
  }
  
   it ("can execute query on mixed bag of numbers, returning as BigDecimals") {    
   val decimals: Seq[BigDecimal] = 
		  conn("""1.0,2.0,'44.2',3.1415926536,xs:double(123.2),xs:integer(12),
				xs:byte(120),xs:long(11111),xs:short(44),xs:int(-5),xs:negativeInteger(-44),
				xs:nonNegativeInteger(45)""")
	val expected=Seq[BigDecimal](1,2,44.2,3.1415926536,123.2,12,120,11111,44,-5,-44,45)			
				
    decimals shouldBe expected
  }
   it ("can handle complex sequences"){
     val refs = toSeqAnyRef(conn("""(1 to  5,44.444,<thing>{for $x in (5,11,12) return <item>{$x}</item>}</thing>,
	    'xxx',<root attr='hello'>somet<mixed>MIX</mixed>hing</root>,
	    <thing attr='alone'/>/@*)"""))
	
	val expected=((1 to 5).map(java.math.BigInteger.valueOf(_)) :+ new java.math.BigDecimal("44.444") :+ <thing>{Seq(5,11,12) map(x=> <item>{x}</item>)}</thing> 
	   :+ "xxx" :+ <root attr='hello'>somet<mixed>MIX</mixed>hing</root> :+ Attribute(None, "attr", Text("alone"), Null))
	
	 refs.size shouldBe expected.size  
	 (refs zip expected).foreach{	 	   
	     case (v:scala.xml.Elem,exp:scala.xml.Elem)=>trimShouldBe(v,exp)
	     case (v,exp)=>v shouldBe exp  	   	  	 
	  }	
   }
    it ("can bind from scala.xml.Elem"){
      val elems:Seq[scala.xml.Elem]=conn("./root()//teddy",
	 <root><child name="Timmy"><teddy><his-teddy>Little Ted</his-teddy>Mr Ted</teddy>
			<teddy><his-teddy>Little Ben</his-teddy>Mr Benson</teddy></child></root>)
			
	  val expected= Seq(<teddy><his-teddy>Little Ted</his-teddy>Mr Ted</teddy>, 
	      <teddy><his-teddy>Little Ben</his-teddy>Mr Benson</teddy>)		

	  trimShouldBe(elems,expected)	  
    }
    it ("can handle prepared expressions and rebinding"){
      val expr2=conn.prepareExpression("""declare variable $list as item()* external;
	    sum($list)""").sequence("list",conn.createSequence((1 to 10).toList))

	  val sum1:Seq[Int]=expr2.executeQuery();
      sum1.head shouldBe 55
      
      expr2.sequence("list",conn.createSequence((1 to 5).toList))
      val sum2:Seq[Int]=expr2.execute();  
      sum2.head shouldBe 15
    }
   
   it ("should leave no dangling sequences or expressions at the end"){  
    getExpressionMap shouldBe empty    
  } 
  
 }
 
 override def afterAll(configMap: org.scalatest.ConfigMap) {
    conn.close()    
  }  
}