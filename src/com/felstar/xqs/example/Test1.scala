package com.felstar.xqs.example

import scala.xml.Attribute
import scala.xml.Elem
import scala.xml.PrettyPrinter
import com.felstar.xqs.XQS._

import javax.xml.xquery.XQResultSequence

//Note, no mention of Dom/Java constructs, just Scala

object Test1{

def main(args: Array[String]): Unit = {
  
  	//val source = new net.sf.saxon.xqj.SaxonXQDataSource()
 //   System.loadLibrary ( "zorba_api" );
  	//val source= new net.xqj.basex.BaseXXQDataSource()
  	val source= new net.xqj.basex.local.BaseXXQDataSource()
    //val source= new org.zorbaxquery.api.xqj.ZorbaXQDataSource()
  	//val source= new net.xqj.sedna.SednaXQDataSource()
  	//val source= new  net.xqj.exist.ExistXQDataSource()
  	//val source= new org.zorbaxquery.api.xqj.ZorbaXQDataSource()
    //val source= new net.xqj.marklogic.MarkLogicXQDataSource()

	if (source.isInstanceOf[net.xqj.basex.BaseXXQDataSource]) {
		source.setProperty("serverName", "localhost")
		source.setProperty("port", "1984")
	}
	else
	if (source.isInstanceOf[net.xqj.sedna.SednaXQDataSource]) {
		source.setProperty("serverName", "localhost")
		source.setProperty("databaseName", "testdb")
	}
   else
	if (source.isInstanceOf[net.xqj.marklogic.MarkLogicXQDataSource]) {
		source.setProperty("serverName", "localhost")
		source.setProperty("port", "8003")
		source.setProperty("mode", "xdbc")
	}

	// Change USERNAME and PASSWORD values
	val USERNAME="admin"
	val PASSWORD="chromestar"

	val conn = if (source.isInstanceOf[net.xqj.basex.local.BaseXXQDataSource]) 
	  source.getConnection else source.getConnection(USERNAME, PASSWORD) 

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

	// ML numberformat exception when not in conformance
	{
	  println("----numberformat-----------")
	 val refs = toSeqAnyRef(conn("(44.4,<xx/>)"))
	  refs.foreach{
		 case x: java.lang.Number => println(x.doubleValue + 1000)
		 case x: Elem => println("Element " + x)
		 case x: Attribute => println("Attribute " + x)
		 case x => println(x + " " + x.getClass)
	 }
	}
	
	println("----complex sequence-----------")
	val refs = toSeqAnyRef(conn("""(1 to  5,44.444,<thing>{10 to 12}</thing>,
	    'xxx',<root attr='hello'>somet<mixed>MIX</mixed>hing</root>,
	    <thing attr='alone'/>/@*)"""))
	refs.foreach{
	 case x: java.lang.Number => println(x.doubleValue + 1000)
	 case x: Elem => println("Element " + x)
	 case x: Attribute => println("Attribute " + x)
	 case x => println(x + " " + x.getClass)
	}

	if (source.isInstanceOf[net.xqj.basex.BaseXXQDataSource]) {
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

	println("----re binding-----------")  
	
	val expr2=conn.prepareExpression("""declare variable $list as item()* external;
	    sum($list)""").sequence("list",conn.createSequence((1 to 10).toList))
	 // execute query doesn't put seq->expression in map, so we don't close expression
	 // Change below to execute() to see second binding complain of closed expression
	val sum1:Seq[Int]=expr2.executeQuery();  		
	sum1 foreach(println)
	
	expr2.sequence("list",conn.createSequence((1 to 5).toList))
	 // we call execute now, which puts seq->expression in map,
	 // so expression is closed. Please don't use it again.
	val sum2:Seq[Int]=expr2.execute();  		
	sum2 foreach(println)
	
	println("----many fluent bindings-----------")  
				
	val str="my text!!"
	 
	val ret2=toSeqAnyRef(conn.prepareExpression(""" 
	 		declare variable $x as xs:integer external;
	 		declare variable $y as xs:integer external;
	 		declare variable $name as xs:string external;
	 		declare variable $mydoc as node() external;
	 		declare variable $list as item()* external; 
			declare variable $date as xs:date external;
	        declare variable $datetime as xs:dateTime external;
	 		($name,' ',$x+$y,'list=',for $i in $list return <x>{$i}</x>,$mydoc,
			$date,$datetime)""")
	    .int("x",1234).int("y",9999).string("name","Dino")
	    .document("mydoc", <somedoc>{str}</somedoc>)
	    // date and datetime come back as XMLGregorianCalendar
	    .date("date",new java.util.GregorianCalendar())
	    .datetime("datetime",new java.util.Date())
	    .sequence("list",conn.createSequence(Seq(1,"some text",99))).execute())
	 // note, we call execute at the end, not executeQuery
	 // this is so that we remember seq->expr mapping for later cleanup/close
	 
	ret2 foreach(x=>println(x+"\n\t"+x.getClass))
			
	{
	  println("----alternate binding method-------")
	  
	  val rows=for (x<- 1 to 3) yield <row>{x}</row>
	 
	  val ret2=toSeqAnyRef(conn.executeQueryWith(""" 
	 		declare variable $x as xs:integer external;
	 		declare variable $y as xs:integer external;
	 		declare variable $name as xs:string external;
	 		declare variable $mydoc as node() external;
	 		declare variable $list as item()* external; 
	 		($name,' ',$x+$y,'list=',for $i in $list return <x>{$i}</x>,$mydoc)""")
	 		{
	         _.int("x",1234).int("y",9999).string("name","Dino")
	         .document("mydoc", <somedoc>{rows}</somedoc>)
	         .sequence("list",conn.createSequence(Seq(1,"some text",99)))
	 		} 
	    )
	
	  ret2 foreach(x=>println(x+"\n\t"+x.getClass))
	  
	}
	{
	 println("----chained queries----method 1-------") 
	 val seq:Seq[Int]=conn("1 to 3")		
	 val sum:Seq[Int]=conn.prepareExpression(
	     """declare variable $list as item()* external;sum($list)""").
	            sequence("list",conn.createSequence(100+:seq)).execute()		           
	 sum foreach(println)
	}
	{
	 println("----chained queries----method 2-------") 
	 val seq:XQResultSequence=conn("1 to 3")		
	 val sum:Seq[Int]=conn.prepareExpression(
	     """declare variable $list as item()* external;sum($list)""").
	            sequence("list",seq).execute()		           
	 sum foreach(println)
	 // first sequence is not consumed via Scala, so needs to be closed by hand
	 closeResultSequence(seq) 
	}
	
	{
	 println("----XXXX--------") 
	 val ret=toSeqAnyRef(conn("(./root()/root/local-name(),<added>added</added>)", <root><empty></empty></root>))		
	 ret foreach(println)
	  
	}
	
	println("----printing expression map, should be empty----")
	val expr=getExpressionMap
	println(expr)
			
	conn.close()
}

}
