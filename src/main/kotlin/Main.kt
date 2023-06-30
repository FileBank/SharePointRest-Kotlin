import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val custNumber = args[0]
        val deptUrl = args[1]
        val libraryName = args[2]
        val libraryTitle = args[3]
        val filename = args[4]
        val filepath = args[5]
        val propertyList = if (args.size > 6) args.sliceArray(6 until args.size) else emptyArray()

        val spCall = SharePointApiCall(custNumber, deptUrl, libraryName, libraryTitle, filename, filepath, propertyList)
        spCall.uploadItem()
    } catch (e: IndexOutOfBoundsException) {
        println("Usage: SharepointRestPass <customer number> <department code> <document library url> <document library name> <file name w/ extension> <file path> [\"propertyName=propertyValue\"]\n\nex: 0180 ar \"AR Invoice\" \"Account Receivable Invoice\" \"0178-01161049.pdf\" \"C:\\Users\\skhan\\Downloads\\0178-01161049.pdf\" \"FB-Invoice FB-Invoice Date=February 17, 2023\"")
        exitProcess(-1)
    }
}
