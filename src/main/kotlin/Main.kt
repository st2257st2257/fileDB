package com.example.fileDB.fileDB

import java.io.File
import java.io.RandomAccessFile


enum class ParserState {
    NONE, SEPARATOR, NEWSTRING, NODE
}

class Token {
    var strNumber   : Int = 0
    var offset      : Int = 0
    var value       : String = ""
}

enum class NodeType {
    NONE, TITLE, DOUBLE
}

class Node {
    var type        : NodeType  = NodeType.NONE
    var sValue      : String    = ""
    var dValue      : Double    = 0.0
    // file location info
    var strNumber   : Int = 0
    var offset      : Int = 0
    var length      : Int = 0
}

class NodeString {
    var index       : Int                = 0
    var strNumber   : Int                = 0
    var nodes       : MutableList<Node>  = mutableListOf()
}

class TokenPos {
    var strNumber   : Int = 0
    var offset      : Int = 0
    var length      : Int = 0
    var what        : Int = 0
}

class FileDB(fileName: String) {

    private val _tokenList      : MutableList<Token>            = mutableListOf()
    private val _nodeStringList : MutableList<NodeString>       = mutableListOf()

    private var _fileName       : String      = fileName
    private var _fileData       : String      = ""
    private var _parserState    : ParserState = ParserState.NONE
    private var maxIDValue      : Int         = 0

    private fun readFile(): Boolean {
        _fileData = File(_fileName).inputStream().readBytes().toString(Charsets.UTF_8)
        if(_fileData.isEmpty())
            return false

        _tokenList.clear()
        var offset      = 0
        var lineNumber  = 0
        var nodeValue   = ""
        _parserState    = ParserState.NONE

        fun addNode() {
            val token        = Token()
            token.strNumber  = lineNumber
            token.offset     = offset
            token.value      = nodeValue
            _tokenList.add( token )
            offset += nodeValue.length
        }

        _fileData.forEach { ch ->
            when (ch) {
                '\t', ' ' -> {
                    if( _parserState == ParserState.NODE ) {
                        addNode()
                        nodeValue = ""
                    }
                    ++offset
                    _parserState = ParserState.SEPARATOR
                }
                '\n' -> {
                    if( _parserState == ParserState.NODE ) {
                        if(nodeValue.isNotEmpty())
                            addNode()
                        nodeValue = ""
                    }
                    ++lineNumber
                    ++offset
                    _parserState = ParserState.NEWSTRING
                }
                else -> {
                    nodeValue   += ch
                    _parserState = ParserState.NODE
                }
            }
        }
        if( nodeValue.isNotEmpty() && _parserState == ParserState.NODE ) {
            addNode()
        }
        readFileStructure()
        return true
    }

    private fun readFileStructure(): List<TokenPos> {
        val posList: MutableList<TokenPos> = mutableListOf()
        val fileData = File("/$_fileName").inputStream().readBytes().toString(Charsets.UTF_8)
        if(fileData.isEmpty())
            return posList

        var nodeValue = ""
        fileData.forEach { ch ->
            when (ch) {
                '\t' -> {          // position info read
                    val fields = nodeValue.split(':')
                    if( fields.size == 4 ) {
                        val tp       = TokenPos()
                        tp.strNumber = fields[0].toInt()
                        tp.offset    = fields[1].toInt()
                        tp.length    = fields[2].toInt()
                        tp.what      = fields[3].toInt()
                        posList.add(tp)
                    }
                    nodeValue = ""
                }
                '\n' -> {   // input finished
                }
                else -> {
                    nodeValue += ch
                }
            }
        }
        return posList
    }

    private fun readStringFromFile(offset: Int, length: Int ): String {
        var value = ""
        val file = RandomAccessFile("/$_fileName", "r")
        file.seek(offset.toLong())
        val position = file.filePointer
        if( position == offset.toLong() ) {
            var b = file.read()
            var read = 1
            while( b!=-1 && read <= length ){
                value += b.toChar()
                b = file.read()
                ++read
            }
        }
        file.close()
        return value
    }

    fun parseFile(): Boolean {
        if( !readFile() )
            return false

        _nodeStringList.clear()
        var nodeString = NodeString()
        var curStrNumber = 0
        _tokenList.forEach{ t ->
            if( curStrNumber != t.strNumber ) {
                nodeString.strNumber = curStrNumber
                _nodeStringList.add( nodeString )
                nodeString           = NodeString()
                curStrNumber         = t.strNumber
            }
            if( t.strNumber == 0 ) { // title
                val node        = Node()
                node.type       = NodeType.TITLE
                node.sValue     = t.value
                node.offset     = t.offset
                node.length     = t.value.length
                nodeString.nodes.add(node)
            } else {                // strings
                val node        = Node()
                node.type       = NodeType.DOUBLE
                node.dValue     = t.value.toDouble()
                node.strNumber  = curStrNumber
                node.offset     = t.offset
                node.length     = t.value.length
                nodeString.nodes.add(node)
            }
        }
        if( nodeString.nodes.size > 0 ) {
            nodeString.strNumber = curStrNumber
            nodeString.index     = maxIDValue
            maxIDValue += 1
            _nodeStringList.add( nodeString )
        }
        return true
    }

    fun readFileByStructure(): Boolean {
        val tokenPosList = readFileStructure()
        if(tokenPosList.isEmpty())
            return false
        _nodeStringList.clear()
        var nodeString       = NodeString()
        var curStrNumber     = 0
        tokenPosList.forEach{ t ->
            if( curStrNumber != t.strNumber ) {
                nodeString.strNumber = curStrNumber
                _nodeStringList.add( nodeString )
                nodeString           = NodeString()
                curStrNumber         = t.strNumber
            }
            if( t.strNumber == 0 ) { // title
                val node        = Node()
                node.type       = NodeType.TITLE
                node.offset     = t.offset
                node.length     = t.length
                nodeString.nodes.add(node)
            } else {                // strings
                val node        = Node()
                node.type       = NodeType.DOUBLE
                node.strNumber  = curStrNumber
                node.offset     = t.offset
                node.length     = t.length
                nodeString.nodes.add(node)
            }
        }
        return true
    }

    fun getStructure(): List<String> {
        return _nodeStringList[0].nodes.map { it.sValue }
    }

    fun get(index: Int): NodeString{
        return _nodeStringList[index]
    }
/*
    fun getByID(ID : Int): NodeString? {
        return _nodeStringList.firstOrNull{it.index == ID}
    }
*/
    fun addDataString( values : List<Double> ) {
        val nodeString       = NodeString()
        nodeString.strNumber = _nodeStringList.size
        values.forEach { currentValue ->
            val node    = Node()
            node.type   = NodeType.DOUBLE
            node.dValue = currentValue
            nodeString.nodes.add(node)
        }
        nodeString.index     = maxIDValue
        maxIDValue += 1
        _nodeStringList.add(nodeString)
        // WITHOUT bufferedWriter
        //File("/$_fileName").appendText("\n")
        //values.forEach{currentValue -> File("/$_fileName").appendText(currentValue.toString()+"\t")}
        // TODO: think about bufferisation of memory
    }

    fun writeFile() {
        try {
            File("/$_fileName").bufferedWriter().use {
                _nodeStringList.forEach { str ->
                    str.nodes.forEach{  node ->
                        if(node.type == NodeType.TITLE ) {
                            it.append("${node.sValue}\t")
                        } else {
                            it.append( "${node.dValue}\t" )
                        }
                    }
                    it.append( "\n" )
                }
            }
        } catch ( e: Exception ) {
            println( e.toString() )
        }
        writeFileStructure()
    }

    private fun writeFileStructure() {
        try {
            File("/$_fileName.pos").bufferedWriter().use {
                _nodeStringList.forEach { str ->
                    str.nodes.forEach{  node ->
                        it.append("${node.strNumber}:${node.offset}:${node.length}:${node.type.ordinal}\t")
                    }
                }
                it.append("\n")
            }

        } catch ( e: Exception ) {
            println( e.toString() )
        }
    }

    fun getStringByIndex( Index: Int ): NodeString? {
        if( Index < 0 || Index + 1 >= _nodeStringList.size )
            return null
        return _nodeStringList[Index + 1] // the first string is title list
    }

    fun getRecordNumber(): Int {
        return _nodeStringList.size - 1
    }

    fun readNodeValueFromFile( node: Node ): String {
        return readStringFromFile( node.offset, node.length )
    }

    fun getStringsForFieldAndInterval(fld: String, left: Double, right: Double ): List<NodeString> {
        val strList : MutableList<NodeString> = mutableListOf()
        if( _nodeStringList.size == 1 )
            return strList
        val titles = _nodeStringList[0]
        val index = titles.nodes.indexOfFirst { it.sValue == fld }
        if( index < 0 )
            return strList
        _nodeStringList.forEach { str ->
            if( left < str.nodes[index].dValue && str.nodes[index].dValue < right )
                strList.add( str )
        }
        return strList
    }

    private fun setStructure(header: List<String>) {
        _nodeStringList.clear()
        val nodeString      = NodeString()
        header.forEach { h->
            val node        = Node()
            node.type       = NodeType.TITLE
            node.sValue     = h
            nodeString.nodes.add(node)
        }
        _nodeStringList.add(nodeString)
    }

    fun addFile( newFileDB: FileDB, key: Int = 0) {
        val newRecordNumber = newFileDB.getRecordNumber()
        val newFileDBHeader = newFileDB.getStructure()
        if( newFileDBHeader.isEmpty() || newRecordNumber == 0 ) // nothing to copy
            return
        if( _nodeStringList.size == 0 ) { // copy
            setStructure( newFileDBHeader )
            for( i in key..newRecordNumber ) {
                val newStr = newFileDB.getStringByIndex( i )?.nodes?.map{ it.dValue }
                if( newStr != null )
                    addDataString(newStr)
            }
            writeFile()
            return
        }
        val newRecordNumber0 = newFileDB.getRecordNumber()
        for( i in key..newRecordNumber0 ) {
            val newStr = newFileDB.getStringByIndex( i )?.nodes?.map{ it.dValue }
            if( newStr != null )
                addDataString(newStr)
        }
        writeFile()
    }
}

fun testDB() {

    // Read .txt file from directory
    val fileDB = FileDB("C:\\sqlite\\test_data.txt")
    fileDB.parseFile()
    val str     =  fileDB.getStringByIndex( 2 )

    fileDB.readFileByStructure()
    fileDB.getStructure().forEachIndexed {  // Check structure of filed
            index, elem -> println("Filed name: $elem  \t value of second string: " +
            if( str != null ) {
                fileDB.readNodeValueFromFile(str.nodes[index])
            } else "_"
    )
    }

    // Write data to file
    for( i in 1..1000 ) {
        val randomValues = listOf(
            (fileDB.getRecordNumber()+1).toDouble(),
            (0..365).random().toDouble(),
            (0..86400).random().toDouble(),
            (-20..35).random().toDouble()
        )
        fileDB.addDataString( randomValues )
    }
    fileDB.writeFile()
    println("Number of items: ${fileDB.getRecordNumber()}")
    fileDB.getStructure().forEachIndexed {  // Check structure of filed
            index, elem -> println("Filed name: $elem \t value of 512 string: " +
            if( fileDB.getStringByIndex( 512 ) != null ) {
                fileDB.getStringByIndex( 512 )!!.nodes[index].dValue.toString()
            } else "_"
    )
    }

    // Get number of string from determinate range of determinate column
    val str1    =  fileDB.getStringsForFieldAndInterval( "id", 3.0, 80.0  )
    println("Get 10-th string:")
    str1[10].nodes.forEach{elem -> println(elem.dValue)}

    // The ability to stitch strings from two different files by key (join operation).
    val fileDB2 = FileDB("C:\\sqlite\\test_data_2.txt")
    fileDB2.parseFile()
    fileDB2.readFileByStructure()

    fileDB.addFile(fileDB2, 8)
}

fun main(args: Array<String>) {
    println("Hello World!")
    testDB()
    println("Program arguments: ${args.joinToString()}")
}