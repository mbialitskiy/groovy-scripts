import nexus_local

def settings_xml= new nexus_local()
if (!settings_xml.CheckSettingsJson()) {
    println "Configuration is not set. Do you want to set configuration? [Yes|No]"
    while (true) {
        yes_no = System.in.newReader().readLine()//it.readLine()

        if (yes_no.equalsIgnoreCase("Yes") || yes_no.equalsIgnoreCase("y")) {
            settings_xml.CreateSettingsJson()
            break
        } else if (yes_no.equalsIgnoreCase("No") || yes_no.equalsIgnoreCase("n")) {
            System.exit(0)
        } else {
            println "The input must be YES|NO"
        }
    }
}


if (!settings_xml.check_settings()) {
    System.exit(0)
}

//settings_xml.nexus_get_list_of_artifacts()

//settings_xml.nexus_push_artifact(55, "hello-world-55.war")

def down_artifact=settings_xml.nexus_download("hello-world","hello-world-11")
settings_xml.deploy_to_tomcat(down_artifact)