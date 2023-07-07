import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.json.*
import java.lang.Exception
import org.apache.http.auth.NTCredentials
import org.apache.http.auth.AuthScope
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.nio.charset.StandardCharsets

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
    private val user = "skhan"
    // read password from txt file
    private val password = "SK562391$" //File("pass.txt").readText()

    private val credentialsProvider: BasicCredentialsProvider = BasicCredentialsProvider()

    private var httpClient: CloseableHttpClient = HttpClients.custom().build()
    private var xToken: String


    private val localHeaders = mutableMapOf("accept" to "application/json;odata=verbose",
                                        "content-type" to "application/json;odata=verbose",
                                        "async" to "false")
    init {
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            NTCredentials(user,password, "Filebank23", "filebankinc")
        )
        httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .build()

        xToken = getToken()
    }


    fun getToken(): String {
        // set API endpoint for getting auth token
        val apiURL = "$rootUrl/_api/contextinfo"

        val httpPost = HttpPost(apiURL)
        httpPost.addHeader("accept","application/json;odata=verbose")
        httpPost.addHeader("content-type","application/json;odata=verbose")
        httpPost.addHeader("async", "false")

        val response = httpClient.execute(httpPost)

        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        println("Status Code: $statusCode\nResponse: $responseBody")

        val jsonResponse = JSONObject(responseBody)

        return jsonResponse.getJSONObject("d")
            .getJSONObject("GetContextWebInformation")
            .getString("FormDigestValue")
    }

    // uploads a file
    fun uploadFile(): CloseableHttpResponse? {
        val uploadApi = "$rootUrl/_api/web/GetFolderByServerRelativeUrl('$libName')/Files/add(url='$fileName',overwrite=true)"
        val file = File(filePath)
        val httpPost = HttpPost(uploadApi)
        httpPost.addHeader("accept","application/json;odata=verbose")
        httpPost.addHeader("content-type","application/json;odata=verbose")
        httpPost.addHeader("async", "false")
        httpPost.addHeader("X-RequestDigest", xToken)
        val builder = MultipartEntityBuilder.create()
        builder.addBinaryBody(
            "file",
            file,
            ContentType.DEFAULT_BINARY,
            file.name
        )
        val entity: HttpEntity = builder.build()

        // Set the request entity
        httpPost.entity = entity
        // Execute the request
        val response = httpClient.execute(httpPost)

        val statusCode = response.statusLine.statusCode
        println(statusCode)
        return response
    }

// returns 403 forbidden
    fun uploadItem(): Int {
        val uploadResponse = uploadFile()

        if (uploadResponse != null) {
            if (uploadResponse.statusLine.statusCode in 200..299) {

                if (propertyList.isNotEmpty()) {

                    val propertyResponse = setListItemProperties()

                    if (propertyResponse.statusLine.statusCode in 200 until 300) {
                        if (DEBUG_MODE) {
                            println("Success")
                        }
                        return 0
                    } else {
                        if (DEBUG_MODE) {
                            println("Unable to set properties, response returned: \n\n\t ${uploadResponse.entity.content.bufferedReader().use { it.readText() }}")
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
                    println("Unable to upload file, response returned: \n\n\t ${uploadResponse.entity.content.bufferedReader().use { it.readText() }}")
                }
                return -1
            }
        }
        return -1
    }
    fun getLists(): JSONObject {
        val apiURL = "${rootUrl}/_api/web/lists/"

        val httpGet = HttpGet(apiURL)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}
        println("Status Code: $statusCode\nResponse: $responseBody")
        return JSONObject(responseBody)
    }


    fun getListItemType() : String {
        val apiURL = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')"
        val httpGet = HttpGet(apiURL)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        val jsonResponse = JSONObject(responseBody)
        val listItemType = jsonResponse.getJSONObject("d").getString("ListItemEntityTypeFullName")
        println(listItemType)
        return listItemType
    }

    fun getList(customFilter: String = ""): JSONObject {
        val apiURL = "${rootUrl}/_api/web/lists/GetByTitle('{$libTitle}')/items?$customFilter"
        val httpGet = HttpGet(apiURL)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        return  JSONObject(responseBody)
    }

    fun getFileNames(customFilter: String = ""): JSONObject {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')/items?\\\$select=FileLeafRef,FileRef&$customFilter"
        val httpGet = HttpGet(urlAPI)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        return  JSONObject(responseBody)
    }

    fun customFilter(filterStr: String): JSONObject {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle('${libTitle}')/${filterStr}"
        val httpGet = HttpGet(urlAPI)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        return  JSONObject(responseBody)
    }

    fun getItemID(): String {
        val urlAPI = "${rootUrl}/_api/web/lists/GetByTitle/${libTitle}/items?\$filter=FileLeafRefeq'${fileName}'"
        val httpGet = HttpGet(urlAPI)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")

        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}
        val responseJson = JSONObject(responseBody)
        println(statusCode)
        println(responseJson)
        return responseJson.getJSONObject("d").getJSONArray("results").getJSONObject(0).getString("ID")
    }

    fun setListItemProperties(): HttpResponse {
        val apiURL = "$rootUrl/_api/web/lists/GetByTitle('${libTitle}')/items('${getItemID()}')"
        val httpPost = HttpPost(apiURL)
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

        val requestBody = payload.toString()
        val requestEntity: HttpEntity = StringEntity(requestBody, StandardCharsets.UTF_8)
        httpPost.entity = requestEntity
        val response: HttpResponse = httpClient.execute(httpPost)

        // Process the response
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText() }

        return response
    }

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


    fun createSite(title: String, url: String, desc: String): HttpResponse {
        val urlAPI = "${rootUrl}/_api/web/webinfos/add"
        val httpPost = HttpPost(urlAPI)
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
        val requestBody = payload.toString()
        val requestEntity: HttpEntity = StringEntity(requestBody, StandardCharsets.UTF_8)
        httpPost.entity = requestEntity
        val response: HttpResponse = httpClient.execute(httpPost)

        // Process the response
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText() }

        return response

    }

    fun deleteSite(url: String): CloseableHttpResponse? {
        val deleteApi = "${rootUrl}/$url/_api/web"
        val httpGet = HttpGet(deleteApi)
        httpGet.addHeader("accept","application/json;odata=verbose")
        httpGet.addHeader("content-type","application/json;odata=verbose")
        httpGet.addHeader("async", "false")
        httpGet.addHeader("X-HTTP-Method", "DELETE")
        httpGet.addHeader("IF-MATCH", "*")


        val response = httpClient.execute(httpGet)
        val statusCode = response.statusLine.statusCode
        val responseBody = response.entity.content.bufferedReader().use { it.readText()}

        return response
    }


}

