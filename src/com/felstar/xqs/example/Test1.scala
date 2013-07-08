package com.felstar.xqs.example

import scala.xml.Attribute
import scala.xml.Elem
import scala.xml.PrettyPrinter

import com.felstar.xqs.XQS._
import com.felstar.xqs.XQS.AllImplicits._

//Note, no mention of Dom/Java constructs, just Scala

object Test1{

	def main(args: Array[String]): Unit = {
	  
	  	//val xqs = new net.sf.saxon.xqj.SaxonXQDataSource()
	  	val xqs= new net.xqj.basex.BaseXXQDataSource()
	  	//val xqs= new net.xqj.sedna.SednaXQDataSource()
	  	//val xqs= new  net.xqj.exist.ExistXQDataSource()
	  	//val xqs= new org.zorbaxquery.api.xqj.ZorbaXQDataSource()

		if (xqs.isInstanceOf[net.xqj.basex.BaseXXQDataSource]) {
			xqs.setProperty("serverName", "localhost")
			xqs.setProperty("port", "1984")
		}
		else
		if (xqs.isInstanceOf[net.xqj.sedna.SednaXQDataSource]) {
			xqs.setProperty("serverName", "localhost")
			xqs.setProperty("databaseName", "testdb")
		}

		// Change USERNAME and PASSWORD values
		val USERNAME="admin"
		val PASSWORD="password"

		val conn = xqs.getConnection(USERNAME, PASSWORD) 

		println("-----1 to 4 as strings----------")

		val strings: Seq[String] = conn("1 to 4")
		strings.foreach(println)

		println("----3 to 8 as ints-----------")
		val ints: Seq[Int] = conn("3 to 8")
		ints.foreach(println)

		println("----a mix of decimals, double, ints etc as decimals-----------")
		val decimals: Seq[BigDecimal] = 
		  conn("""1.0,2.0,3.1415926536,xs:double(123.2),xs:integer(12),
				xs:byte(120),xs:long(11111),xs:short(44),xs:int(-5),xs:negativeInteger(-44),
				xs:nonNegativeInteger(45)""")
		decimals.foreach(println)

		println("----above < 100 -----------")
		println(for (x<-decimals if x<100) yield x)

		println("----above < 100 with filter-----------")
		println(decimals filter (_<100) mkString(", "))

		println("----complex sequence-----------")
		val refs = toSeqAnyRef(conn("""(1 to  5,44.444,<thing>{10 to 12}</thing>,
		    'xxx',<root attr='hello'>somet<mixed>MIX</mixed>hing</root>,
		    <thing attr='alone'/>/@*)"""))
		refs.foreach(_ match {
		 case x: java.lang.Number => println(x.doubleValue() + 1000)
		 case x: Elem => println("Element " + x)
		 case x: Attribute => println("Atrribute " + x)
		 case x => println(x + " " + x.getClass())
		})

		if (xqs.isInstanceOf[net.xqj.basex.BaseXXQDataSource]) {
			println("----db query-----------")
			val xmls: Seq[scala.xml.Elem] = 
			  conn("collection('shaks200')/PLAY[contains(lower-case(TITLE),'tragedy')]")

			for ((xml, idx) <- xmls.view.zipWithIndex if idx < 5) {
				println(idx)
				println(xml \ "TITLE" text)
			}
		}
		
		println("----context binding from scala.xml.Elem with preparedExpression-------")
		
		val elems:Seq[scala.xml.Elem]=conn("./root()//teddy",
		 <root><child name="Timmy"><teddy><his-teddy>Little Ted</his-teddy>Mr Ted</teddy>
			<teddy><his-teddy>Little Ben</his-teddy>Mr Benson</teddy></child></root>)

		val pp=new PrettyPrinter(80,2)

		elems foreach(x=>println(pp.format(x)))    

		println("----output method 1----")
		elems \ "his-teddy" foreach(x=>println(x.text))

		println("----output method 2----")
		elems \ "his-teddy" map (_.text) foreach(println)

		println("----many fluent bindings-----------")  
					
		val str="my text!!"
		 
		val ret2=toSeqAnyRef(conn.prepareExpression(""" 
		 		declare variable $x as xs:integer external;
		 		declare variable $y as xs:integer external;
		 		declare variable $name as xs:string external;
		 		declare variable $mydoc as node() external;
		 		declare variable $list as item()* external; 
		 		($name,' ',$x+$y,'list=',for $i in $list return <x>{$i}</x>,$mydoc)""")
		    .int("x",1234).int("y",9999).string("name","Dino")
		    .document("mydoc", <somedoc>{str}</somedoc>)
		    .sequence("list",conn.createSequence(Seq(1,"some text",99))).executeQuery())
		 
		ret2 foreach(x=>println(x+"\n\t"+x.getClass))
		
		conn.close()
	}
}