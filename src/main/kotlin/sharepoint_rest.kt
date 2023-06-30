import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.json.*
import java.lang.Exception

val DEBUG_MODE = true

class SharePointApiCall(
    customerNumber: String,
    departmentUrl: String,
    private val libName: String,
    private val libTitle: String,
    private val fileName: String,
    private val filePath: String,
    private val propertyList: Array<String>
) {
    // set root url based on input
    private val rootUrl = "http://sp-f/fb/$customerNumber/$departmentUrl"

    // set username for authentication
    private val user = "FILEBANKINC\\dtrivisani"
    // read password from txt file
    private val password = File("pass.txt").readText()
    // http thing
    private val client = OkHttpClient.Builder().build()

    private val localHeaders = mutableMapOf("accept" to "application/json;odata=verbose",
                                        "content-type" to "application/json;odata=verbose",
                                        "async" to "false")
    init {
        setHeaders()
    }


    fun getToken(): String {
        // set API endpoint for getting auth token
        val contextinfoApi = "$rootUrl/_api/contextinfo"
        val request = Request.Builder()
            .url(contextinfoApi)
            .header("accept", "application/json;odata=verbose")
            .addHeader("content-type", "application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val client = OkHttpClient()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        val jsonResponse = JSONObject(responseBody)
        return jsonResponse.getJSONObject("d")
            .getJSONObject("GetContextWebInformation")
            .getString("FormDigestValue")
    }

    // uploads a file
    fun uploadFile(): Response {
        val uploadApi = "$rootUrl/_api/web/GetFolderByServerRelativeUrl('$libName')/Files/add(url='$fileName',overwrite=true)"
        val file = File(filePath)
        val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(uploadApi)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        return client.newCall(request).execute()
    }


    fun uploadItem(): Int {
        val uploadResponse = uploadFile()

        if (uploadResponse.code in 200..299) {

            if (propertyList.isNotEmpty()) {

                val propertyResponse = setListItemProperties()

                if (propertyResponse.code in 200 until 300) {
                    if (DEBUG_MODE) {
                        println("Success")
                    }
                    return 0
                } else {
                    if (DEBUG_MODE) {
                        println("Unable to set properties, response returned: \n\n\t ${propertyResponse.body}")
                    }
                    return 1
                }
            }
            else {
                if (DEBUG_MODE) {
                    println("Success")
                }
                return 0
            }
        }
        else {
            if (DEBUG_MODE) {
                println("Unable to upload file, response returned: \n\n\t ${uploadResponse.body}")
            }
            return -1
        }
    }
    fun getLists(): JSONObject {
        val apiURL = "${rootUrl}/_api/web/lists/"

        val request = Request.Builder()
            .url(apiURL)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        return JSONObject(responseBody)
    }


    fun getListItemType() : String {
        val apiURL = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')"
        val request = Request.Builder()
            .url(apiURL)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        val jsonResponse = JSONObject(responseBody)
        val listItemType = jsonResponse.getJSONObject("d").getString("ListItemEntityTypeFullName")

        return listItemType
    }

    fun getList(customFilter: String = ""): JSONObject {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('{$libTitle}')/items?$customFilter"
        val request = Request.Builder()
            .url(urlAPI)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        return  JSONObject(responseBody)
    }

    fun getFileNames(customFilter: String = ""): JSONObject {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')/items?\\\$select=FileLeafRef,FileRef&$customFilter"
        val request = Request.Builder()
            .url(urlAPI)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        return  JSONObject(responseBody)
    }

    fun customFilter(filterStr: String): JSONObject {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')/${filterStr}"
        val request = Request.Builder()
            .url(urlAPI)
            .header("accept","application/json;odata=verbose")
            .addHeader("content-type","application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        return  JSONObject(responseBody)
    }

    fun getItemID(): String {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')/items?\$filter=FileLeafRef eq '${fileName}'"
        val request = Request.Builder()
            .url(urlAPI)
            .header("accept", "application/json;odata=verbose")
            .addHeader("content-type", "application/json;odata=verbose")
            .addHeader("async", "false")
            .build()
        val response: Response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        val responseJson = JSONObject(responseBody)
        return responseJson.getJSONObject("d").getJSONArray("results").getJSONObject(0).getString("ID")
    }

    fun setListItemProperties(): Response {
        val apiURL = "$rootUrl/_api/web/lists/GetByTitle('${libTitle}')/items('${getItemID()}')"

        val payload = JSONObject()
        payload.put("__metadata", JSONObject().put("type", getListItemType()))

        for (itemProperty in propertyList) {
            val splitProperty = itemProperty.split("=")

            var propertyName = splitProperty[0].replace(" ", "_x0020_")
            propertyName = propertyName.replace("-", "_x002d_")
            propertyName = propertyName.replace("...", "_x002e_")
            val propertyValue = splitProperty[1]

            payload.put(propertyName, propertyValue)
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(apiURL)
            .header("accept", "application/json;odata=verbose")
            .addHeader("content-type", "application/json;odata=verbose")
            .addHeader("async", "false")
            .addHeader("X-HTTP-Method", "MERGE")
            .addHeader("IF-MATCH", "*")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        return client.newCall(request).execute()
    }

    /*
    * fun setHeaders() {
    try {
        headers["X-RequestDigest"] = getToken()
    } catch (e: Exception) {
        if (DEBUG_MODE) {
            println("Error getting authorization token")
        }
        return -1
    }
    return 0
}*/
    fun setHeaders(): Int {
        try {
            localHeaders["X-RequestDigest"] = getToken()
        } catch (e: Exception) {
            if(DEBUG_MODE){
                println("Error getting authorization token")
            }
            return -1
        }
        return 0
    }


    fun createSite(title: String, url: String, desc: String): Response {
        val urlAPI = "${rootUrl}/_api/web/webinfos/add"
        val payload = JSONObject()
        payload.put("parameters", JSONObject().apply {
            put("__metadata", JSONObject().apply {
                put("type", "SP.WebInfoCreationInformation")
            })
            put("Url", url)
            put("Title", title)
            put("Description", desc)
            put("Language", 1033)
            put("WebTemplate", "STS")
            put("UseUniquePermissions", true)
        })
        val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(urlAPI)
            .header("accept", "application/json;odata=verbose")
            .addHeader("content-type", "application/json;odata=verbose")
            .addHeader("async", "false")
            .post(requestBody)
            .build()

        return client.newCall(request).execute()

    }

    fun deleteSite(url: String): Response {
        val deleteApi = "${rootUrl}/$url/_api/web"
        val request = Request.Builder()
            .url(deleteApi)
            .header("accept", "application/json;odata=verbose")
            .addHeader("content-type", "application/json;odata=verbose")
            .addHeader("async", "false")
            .addHeader("X-HTTP-Method", "DELETE")
            .addHeader("IF-MATCH", "*")
            .build()

        return client.newCall(request).execute()
    }


}

