import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

HOME_DIR = './'
jenkins_job_dir='/root/.jenkins/workspace/MNT-CD-module9-build-job/home-task/war/target/'
settings_xml = [:]
nexus_current_builds = []
jenkins_vag = [:]

parse_input_artiface(args[1].toString())

switch (args[2]) {

    case 'pull':
        println "this is pull"
        nexus_download(args[0], args[1])
        deploy_to_tomcat(args[1])
        break
    case 'push':
        println "this is push"
       // nexus_download(args[0], args[1])
        nexus_push_artifact(jenkins_vag['build_number'],args[1].toString()+'.war')
       // deploy_to_tomcat(jenkins_job_dir+args[1])
        break
}

    Boolean CheckSettingsJson() {

        def settings_file = new File(HOME_DIR + "/settings.xml")
        if (settings_file.exists()) {
            return Boolean.TRUE
        } else {
            return Boolean.FALSE
        }
    }

    void read_settings_xml() {
        def settings_file = new File(HOME_DIR + "/settings.xml")
        def parser = new JsonSlurper()
        def result = parser.parse(settings_file)
        settings_xml.put('username', result['username'])
        settings_xml.put('password', result['password'])
        settings_xml.put('url', result['url'])
        settings_xml.put('rep_name', result['rep_name'])
        settings_xml.put('proj_name', result['proj_name'])
        settings_xml.put('group_name', result['group_name'])
    }

    void CreateSettingsJson() {

        println "Enter User Name to connect to Nexus Storage:"
        settings_xml.put('username', System.in.newReader().readLine())
        println "Enter User Pasword to connect to Nexus Storage:"
        settings_xml.put('password', System.in.newReader().readLine())
        println "Enter Nexus Storage URL:"
        settings_xml.put('url', "http://" + System.in.newReader().readLine())
        println "Enter Nexus repository name :"
        settings_xml.put('rep_name', System.in.newReader().readLine())
        println "Enter Nexus project name :"
        settings_xml.put('proj_name', System.in.newReader().readLine())
        println "Enter Nexus group name :"
        settings_xml.put('group_name', System.in.newReader().readLine())
        def settings_file = new File(HOME_DIR + '/settings.xml')
        settings_file << new JsonBuilder(settings_xml).toPrettyString()
    }

    Boolean check_settings() {
        if (!settings_xml) {
            def json_parser = new JsonSlurper()
            File settings_file = new File("${HOME_DIR}settings.xml")
            def settings_file_text = settings_file.getText()
            settings_xml = json_parser.parseText(settings_file_text)
        }
        URL url = new URL(settings_xml['url'])
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod('GET')
        connection.connect()

        if (connection.getResponseCode() == 200) {
            println "Nexus server found"
            return Boolean.TRUE
        } else {
            println "Nexus not found"
            return Boolean.FALSE
        }
        connection.disconnect()
    }

    String nexus_get_encoded_creds() {
        def auth_string = jenkins_vag['nexus_username'] + ":" + jenkins_vag['nexus_password']
        //def auth_string=username.toString()+":"+password.toString()
        return auth_string.toString().bytes.encodeBase64().toString()

    }

    void nexus_get_list_of_artifacts() {

        def nexus_repos_json = settings_xml['url'] + '/service/siesta/rest/beta/search?repository=' + settings_xml['rep_name'] + '&&maven.extension=war'
        URL url = new URL(nexus_repos_json)

        URLConnection connection = url.openConnection()
        connection.setRequestProperty("Authorization", "Basic " + nexus_get_encoded_creds())
        connection.connect()

        def read_buffer = connection.inputStream.getText()
        def parser = new JsonSlurper()
        def result = parser.parseText(read_buffer.toString())

        result['items'].each {
            nexus_current_builds.add(it.version)
        }


    }

    void nexus_push_artifact(build_number, upload_file) {

        //check is there such build
       /* if (nexus_current_builds.contains(build_number.toString())) {
            println "Artifact with build ${build_number} already exists in Nexus"
            return
        }*/
        //def upload_string = settings_xml['url'] + '/repository/' + settings_xml['rep_name'] + '/' + settings_xml['group_name'] + '/' + settings_xml['proj_name'] + '/' + "${build_number}" + "/" + "${upload_file}"
        def upload_string = jenkins_vag['nexus_url'] + '/repository/' + args[0] + '/' + jenkins_vag['group_id'] + '/' + jenkins_vag['proj_name'] + '/' + jenkins_vag['build_number'] +'/'+ "${upload_file}"
        println upload_string
        URL url = new URL(upload_string)
        String path = jenkins_job_dir+ upload_file.toString()
        File file_send_to_nexus = new File(path)


        HttpURLConnection connection = url.openConnection()
        connection.setRequestMethod("PUT")
        connection.setRequestProperty("Authorization", "Basic " + nexus_get_encoded_creds())
        connection.addRequestProperty("Content-Type", "multipart/form-data")
        connection.addRequestProperty("Content-Length", String.valueOf(file_send_to_nexus.length()))
        connection.setDoOutput(true)
        connection.connect()

        OutputStream output = connection.getOutputStream()
        InputStream file_input_stream = new FileInputStream(file_send_to_nexus)
        BufferedInputStream file_buffered_input_stream = new BufferedInputStream(file_input_stream)

        byte[] streamFileBytes = file_send_to_nexus.getBytes()
        int bytesRead = 0
        int totalBytesRead = 0

        while ((bytesRead = file_buffered_input_stream.read(streamFileBytes)) > 0) {
            output.write(streamFileBytes, 0, bytesRead)
            output.flush()

            totalBytesRead += bytesRead
        }
        connection.responseCode
        println totalBytesRead
        output.close()
        //nexus_get_list_of_artifacts()
    }

    void parse_input_artiface(String artifact) {

        jenkins_vag.put('proj_name', artifact.substring(0, artifact.indexOf(artifact.find(/\d/).toString()) - 1))
        jenkins_vag.put('build_number', artifact.replaceAll(/[a-zA-Z\-]/, ""))
        jenkins_vag.put('group_id', jenkins_vag['proj_name'])
        jenkins_vag.put('nexus_url', 'http://nexus.local:8081') //nexus.local
        jenkins_vag.put('tomcat_url', 'http://tomcat.local:8080') //tomcat.local
        jenkins_vag.put('nexus_username','jenkins')
        jenkins_vag.put('nexus_password','jenkins')
        jenkins_vag.put('tomcat_username','deploy')
        jenkins_vag.put('tomcat_password','deploy')

    }

    String get_nexus_download_link(repo_name, artifact) {
       // parse_input_artiface(artifact.toString())
        def nexus_get_url = jenkins_vag['nexus_url'] + '/service/siesta/rest/beta/search/assets?repository=' + args[0].toString() + "&version=${jenkins_vag['build_number'].toString()}" + "&maven.extension=war"

        URL url = new URL(nexus_get_url)
        URLConnection connection = url.openConnection()
        connection.setRequestProperty("Authorization", "Basic " + nexus_get_encoded_creds())
        //connection.setConnectTimeout(1)
        connection.connect()

        def read_buffer = connection.inputStream.getText()
        def parser = new JsonSlurper()
        def result = parser.parseText(read_buffer.toString())


        String download_string = result.items['downloadUrl'].toString()

        //download_string = download_string.replaceAll("http://nexus", "${jenkins_vag['nexus_url']}")
        download_string = download_string.substring(1, download_string.length() - 1)

        return download_string

    }

    String nexus_download(repo_name, artifact) {
        //parse_input_artiface(artifact.toString())
        def download_filename = jenkins_vag['proj_name'].toString() + '-' + jenkins_vag['build_number'].toString() + '.war'
        //download
        def download = new URL(get_nexus_download_link(repo_name, artifact))
        URLConnection con = download.openConnection()
        con.setRequestProperty("Authorization", "Basic " + nexus_get_encoded_creds())
        InputStream input = con.getInputStream()

        byte[] buffer = new byte[4096]
        int n

        OutputStream output = new FileOutputStream(download_filename);
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n)
        }
        output.close()

        return download_filename
    }

    void deploy_to_tomcat(String deploy_filename) {
        //deploy

        File downloaded_artifact = new File(deploy_filename+'.war')
        def deploy_link = "${jenkins_vag['tomcat_url']}/manager/text/deploy?path=/${jenkins_vag['proj_name']}&update=true"

        def tomcat = new URL(deploy_link)
        HttpURLConnection connect_to_tomcat = tomcat.openConnection()
        connect_to_tomcat.setRequestProperty("Authorization", "Basic " + "deploy:deploy".bytes.encodeBase64().toString())
        connect_to_tomcat.setRequestMethod("PUT")
        connect_to_tomcat.addRequestProperty("Content-Type", "multipart/form-data")
        connect_to_tomcat.addRequestProperty("Content-Length", String.valueOf(downloaded_artifact.length()))
        connect_to_tomcat.setDoOutput(true)

        connect_to_tomcat.connect()


        OutputStream output = connect_to_tomcat.getOutputStream()
        InputStream file_input_stream = new FileInputStream(downloaded_artifact)
        BufferedInputStream file_buffered_input_stream = new BufferedInputStream(file_input_stream)

        byte[] streamFileBytes = downloaded_artifact.getBytes()
        int bytesRead = 0
        int totalBytesRead = 0

        while ((bytesRead = file_buffered_input_stream.read(streamFileBytes)) > 0) {
            output.write(streamFileBytes, 0, bytesRead)
            output.flush()

            totalBytesRead += bytesRead
        }
        println totalBytesRead
        output.close()
        println connect_to_tomcat.responseCode
    }

/*if (!CheckSettingsJson()) {
    println "Configuration is not set. Do you want to set configuration? [Yes|No]"
    while (true) {
        yes_no = System.in.newReader().readLine()//it.readLine()

        if (yes_no.equalsIgnoreCase("Yes") || yes_no.equalsIgnoreCase("y")) {
            new_artifact.CreateSettingsJson()
            break
        } else if (yes_no.equalsIgnoreCase("No") || yes_no.equalsIgnoreCase("n")) {
            System.exit(0)
        } else {
            println "The input must be YES|NO"
        }
    }
}


if (!check_settings()) {
    System.exit(0)
}
*/
//settings_xml.nexus_get_list_of_artifacts()

//settings_xml.nexus_push_artifact(55, "hello-world-55.war")

//args[0] - reponame, args[1] - artifact name , args[2] - command





