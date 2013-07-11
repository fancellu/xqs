![XQS Logo](http://felstar.com/projects/xqs/img/xqs-cliff.png)
# XQuery for Scala (XQS)

*Author: [Dino Fancellu](http://dinofancellu.com)*

**XQS** is a Scala Library to invoke XQuery against an XML DataSource such as MarkLogic, eXist, BaseX and Sedna as well as Saxon, Zorba and Oracle XDB while eliminating vendor lock in.

It should work with any compliant **XQJ** driver, having already been tested against the **[XQJ.net](http://xqj.net)** drivers ( **BaseX**, **Sedna**, **eXist**, **Marklogic** ) and **Saxon**

Requires Scala 2.10+

Firstly, make sure that your **XQJ** drivers jars are included and are working.
Perhaps run some java to make sure its all up and running.

Then add

> com.felstar.xqs.XQS.scala 

to your code base. XQS is implemented in one file!

Then in your Scala include in the following:


	import com.felstar.xqs.XQS._
	import com.felstar.xqs.XQS.AllImplicits._

The next few steps are very familiar to any XQJ Developer:

Pull in your desired XQDataSource, e.g.
```scala
val source= new net.xqj.basex.BaseXXQDataSource()
```
If you are connecting to an **XQJ** datatbase (like **BaseX**) and not an **XQJ** processor (like Saxon) you may then need to specify login properties, e.g.
```scala
source.setProperty("serverName", "localhost")
source.setProperty("port", "1984")
```
Similarly you may well need to login
```scala
val conn = source.getConnection("USERNAME", "PASSWORD")
```
# Now the magic starts, some example code: #

## 1 to 4 as strings ##
```scala
val strings: Seq[String] = conn("1 to 4")
strings.foreach(println)
```

>     1
>     2
>     3
>     4
  
## 3 to 8 as ints ##
```scala
val ints: Seq[Int] = conn("3 to 8")
ints.foreach(println)
```
>     3
>     4
>     5
>     6
>     7
>     8

## A mix of decimals, double, ints etc as decimals ##
```scala
val decimals: Seq[BigDecimal] = 
	  conn("""1.0,2.0,3.1415926536,xs:double(123.2),xs:integer(12),
			xs:byte(120),xs:long(11111),xs:short(44),xs:int(-5),xs:negativeInteger(-44),
			 xs:nonNegativeInteger(45)""")
	decimals.foreach(println)
```
>     1
>     2
>     3.1415926536
>     123.2
>     12
>     120
>     11111
>     44
>     -5
>     -44
>     45
  
## Above < 100 ##
```scala	
println(for (x<-decimals if x<100) yield x)
```  
>     List(1, 2, 3.1415926536, 12, 44, -5, -44, 45)

## Above < 100 with filter ##
```scala
println(decimals filter (_<100) mkString(", "))
```  
>     1, 2, 3.1415926536, 12, 44, -5, -44, 45

## Complex sequence##
```scala
val refs = toSeqAnyRef(conn("""(1 to  5,44.444,<thing>{10 to 12}</thing>,
    'xxx',<root attr='hello'>somet<mixed>MIX</mixed>hing</root>,
    <thing attr='alone'/>/@*)"""))
 refs.foreach(_ match {
  case x: java.lang.Number => println(x.doubleValue() + 1000)
  case x: Elem => println("Element " + x)
  case x: Attribute => println("Atrribute " + x)
  case x => println(x + " " + x.getClass())
})
```
>     1001.0
>     1002.0
>     1003.0
>     1004.0
>     1005.0
>     1044.444
>     Element <thing>10 11 12</thing>
>     xxx class java.lang.String
>     Element <root attr="hello">somet<mixed>MIX</mixed>hing</root>
>     Atrribute  attr="alone"

## A Database query ##
```scala
val xmls: Seq[scala.xml.Elem] = 
  conn("collection('shaks200')/PLAY[contains(lower-case(TITLE),'tragedy')]")

for ((xml, idx) <- xmls.view.zipWithIndex if idx < 5) {
	println(idx)
	println(xml \ "TITLE" text)
}
```

>     0
>     The Tragedy of Antony and Cleopatra
>     1
>     The Tragedy of Coriolanus
>     2
>     The Tragedy of Hamlet, Prince of Denmark
>     3
>     The Tragedy of Julius Caesar
>     4
>     The Tragedy of King Lear	

##Context binding from scala.xml.Elem
```scala
val elems:Seq[scala.xml.Elem]=conn("./root()//teddy",
 <root><child name="Timmy"><teddy><his-teddy>Little Ted</his-teddy>Mr Ted</teddy>
		<teddy><his-teddy>Little Ben</his-teddy>Mr Benson</teddy></child></root>)

val pp=new PrettyPrinter(80,2)

elems foreach(x=>println(pp.format(x)))    

println("----output method 1----")
elems \ "his-teddy" foreach(x=>println(x.text))

println("----output method 2----")
elems \ "his-teddy" map (_.text) foreach(println)
```

>     <teddy>
>      <his-teddy>Little Ted</his-teddy>
>      Mr Ted
>     </teddy>
>     <teddy>
>      <his-teddy>Little Ben</his-teddy>
>      Mr Benson
>     </teddy>
>     ----output method 1----
>     Little Ted
>     Little Ben
>     ----output method 2----
>     Little Ted
>     Little Ben

##Many fluent Bindings
```scala
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
    .sequence("list",conn.createSequence(List(1,"some text",99))).execute())
 		 // note, we call execute at the end, not executeQuery
		 // this is so that we remember seq->expr mapping for later cleanup/close
  
ret2 foreach(x=>println(x+"\n\t"+x.getClass))
```
>     Dino
>       class java.lang.String	 
>     	
> 		class java.lang.String
>     11233
>       class java.math.BigInteger
>     list=
>       class java.lang.String
>     <x>1</x>
>       class scala.xml.Elem
>     <x>some text</x>
>       class scala.xml.Elem
>     <x>99</x>
>       class scala.xml.Elem
>     <somedoc>my text!!</somedoc>
>       class scala.xml.Elem

##A few items of note

The connection is never closed by XQS, in the same way in that it is not opened by it either. That is up to you.
Expressions and ResultSequences are closed as soon as the ResultSequence is pulled into Scala, so it shouldn't leave dangling objects leading to memory leaks, even when we throw an exception.

*Any crimes I have committed against the gods of Scala I apologise for. Still getting my head around the power and majesty of Scala. If I had more time I'm sure I could made it smaller.*

## *A big thank you to Charles Foster of [XQJ.net](http://xqj.net) for the inspiration* ##
