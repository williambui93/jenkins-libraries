/**@
    Generate deployment yaml file for kubernetes deployment

    parameters : 
        type : Project type (fe/be)
        deploymentName : Kubernetes deployment/workload name
        namespace : Namespace of workload to deploy
        imageName : Image name to use in container

        # Deployment
        configMapFileName : Configuration file name in container
        configContainerPath (optional) : Path of full path config in container

        # Service
        port : Port to expose in service
        targetPort : Container forwarded port
        serviceType : Kube Service Type
        
        # Configmap
        configPath : Config file name to store in configmap
*/

import org.yaml.snakeyaml.Yaml

def call(Map config = [:]) {
    if (isUnix()) {
            configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap'), configFile(fileId: 'GeneralConfig', targetLocation: './GeneralConfig.json', variable: 'GeneralConfig')]) {
            def matchers = ~ /.*-(frontend|fe)/
            config.type = config.type ? config.type: (matchers.matcher(config.deploymentName).matches() ? "fe": "be")

            // Namespace
            Map namespace = [apiVersion: "v1", kind: "Namespace", metadata: [name: config.namespace]]
            if (!fileExists('namespace.yaml')) {
                writeYaml(data: namespace, file: "namespace.yaml")
            }

            // Deployment
            def deployment = readYaml(file: 'deployment.yaml')
            deployment.metadata.name = config.deploymentName
            deployment.metadata.namespace = config.namespace
            deployment.metadata.labels.app = config.deploymentName
            deployment.spec.selector.matchLabels.app = config.deploymentName
            deployment.spec.template.metadata.labels.app = config.deploymentName
            deployment.spec.template.spec.volumes[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.volumes[0].configMap.name = """${config.deploymentName}-appsettings"""
            deployment.spec.template.spec.containers[0].name = config.deploymentName
            deployment.spec.template.spec.containers[0].image = config.imageName
            deployment.spec.template.spec.containers[0].volumeMounts[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath = (config.configContainerPath ? config.configContainerPath: (config.type == 'fe' ? "/usr/share/nginx/html/assets/config/${config.configMapFileName}": "/app/${config.configMapFileName}"))
            deployment.spec.template.spec.containers[0].volumeMounts[0].subPath = config.configMapFileName

            sh "rm ./deployment.yaml"
            writeYaml(data: deployment, file: "deployment.yaml")

            // Service
            def service = readYaml(file: 'service.yaml')
            service.metadata.name = config.deploymentName
            service.metadata.namespace = config.namespace
            service.spec.selector.app = config.deploymentName
            service.spec.ports[0].port = config.port
            service.spec.ports[0].targetPort = config.targetPort
            service.spec.type = config.serviceType

            sh "rm ./service.yaml"
            writeYaml(data: service, file: "service.yaml")

            // ConfigMap
            def configmap = readYaml(file: 'configmap.yaml')
            configmap.metadata.name = """${config.deploymentName}-appsettings"""
            configmap.metadata.namespace = config.namespace
            def data = config.configPath ? readFile(config.configPath): "{}"

            if (config.type == 'fe')
            {
                def jsonString = data
                def jsonSetting = readFile(file: 'GeneralConfig.json')
                
                // Membaca JSON
                jsonString = jsonString.replace("\\", "/")
                
                //hapus comment di json
                def jsonStringWithoutComments = jsonString.replaceAll(/\/\*(?:[^*]|(?:\*+[^*\/]))*\*\//, '')
                
                def jsonAppSetting = new JsonSlurper().parseText(jsonStringWithoutComments)
                def jsonConfSetting = new JsonSlurper().parseText(jsonSetting)
                
                //Ubah Logging
                if("Logging" in jsonAppSetting."ConnectionStrings".keySet())
                {
                    if(jsonConfSetting."Logging"."DataBaseType" == "POSTGRESQL")
                    {
                        def DBString = jsonAppSetting."ConnectionStrings"."Logging"."DataBasePostgreSQL"
                            
                        def DBStringSplitData = DBString.split(';')
                        def keyValuePairs = [:]
                        
                        DBStringSplitData.each { pair ->
                            def keyValue = pair.split('=')
                            def key = keyValue[0].trim()
                            def value = keyValue[1].trim()
                            keyValuePairs[key] = value
                        }
                        
                        keyValuePairs['Host'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Host"
                        keyValuePairs['User ID'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."User ID"
                        keyValuePairs['Password'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Password"
                        keyValuePairs['Port'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Port"
                        keyValuePairs['Database'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."DatabaseName"
                        
                        
                        def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                        
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseType" = jsonConfSetting."Logging"."DataBaseType"
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBasePostgreSQL" = data
                    }
                    else if(jsonConfSetting."Logging"."DataBaseType" == "SSMS")
                    {
                        def DBString = jsonAppSetting."ConnectionStrings"."Logging"."DataBaseSSMS"
                            
                        def DBStringSplitData = DBString.split(';')
                        def keyValuePairs = [:]
                        
                        DBStringSplitData.each { pair ->
                            def keyValue = pair.split('=')
                            def key = keyValue[0].trim()
                            def value = keyValue[1].trim()
                            keyValuePairs[key] = value
                        }
                        
                        keyValuePairs['Server'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Server"
                        keyValuePairs['User ID'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."User ID"
                        keyValuePairs['Password'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."Password"
                        keyValuePairs['Database'] = jsonConfSetting."Logging"."DataBasePostgreSQL"."DatabaseName"
                        
                        
                        def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                        
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseType" = jsonConfSetting."Logging"."DataBaseType"
                        jsonAppSetting."ConnectionStrings"."Logging"."DataBaseSSMS" = data
                    }
                }
                
                
                // Ubah Database
                for(DatabaseName in jsonAppSetting."ConnectionStrings") {
                 targetField = DatabaseName.key;
                 if (jsonConfSetting."Database"."DataBaseType" == "POSTGRESQL")
                 {
                     if (DatabaseName.key in jsonConfSetting."Database"."DataBasePostgreSQL"."DatabaseName".keySet())
                     {
                        for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                        {
                            if(itemsSetting.key == "DataBasePostgreSQL")
                            {
                                def DBString = itemsSetting.value
                                
                                def DBStringSplitData = DBString.split(';')
                                def keyValuePairs = [:]
                                
                                DBStringSplitData.each { pair ->
                                    def keyValue = pair.split('=')
                                    def key = keyValue[0].trim()
                                    def value = keyValue[1].trim()
                                    keyValuePairs[key] = value
                                }
                                
                                keyValuePairs['Host'] = jsonConfSetting."Database"."DataBasePostgreSQL"."Host"
                                keyValuePairs['User ID'] = jsonConfSetting."Database"."DataBasePostgreSQL"."User ID"
                                keyValuePairs['Port'] = jsonConfSetting."Database"."DataBasePostgreSQL"."Port"
                                keyValuePairs['Password'] = jsonConfSetting."Database"."DataBasePostgreSQL"."Password"
                                keyValuePairs['Database'] = jsonConfSetting."Database"."DataBasePostgreSQL"."DatabaseName"."$targetField"
                                
                                
                                def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                
                                jsonAppSetting."ConnectionStrings"."$targetField"."DataBasePostgreSQL" = data
                                
                                
                            }
                            else if(itemsSetting.key == "SQLConnSP")
                            {
                                def DBString = itemsSetting.value
                                
                                def DBStringSplitData = DBString.split(';')
                                def keyValuePairs = [:]
                                
                                DBStringSplitData.each { pair ->
                                    def keyValue = pair.split('=')
                                    def key = keyValue[0].trim()
                                    def value = keyValue[1].trim()
                                    keyValuePairs[key] = value
                                }
                                
                                keyValuePairs['Data Source'] = jsonConfSetting."Database"."DataBasePostgreSQL"."Server"
                                keyValuePairs['user id'] = jsonConfSetting."Database"."DataBasePostgreSQL"."User ID"
                                keyValuePairs['Password'] = jsonConfSetting."Database"."DataBasePostgreSQL"."Password"
                                keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBasePostgreSQL"."DatabaseName"."$targetField"
                                
                                
                                def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                
                                jsonAppSetting."ConnectionStrings"."$targetField"."SQLConnSP" = data
                                
                            }
                        }
                     
                    }
                 
                 }
                 else if (jsonConfSetting."Database"."DataBaseType" == "SSMS")
                 {
                     if('DataBaseSSMS' in jsonAppSetting."ConnectionStrings"."$targetField")
                     {
                         if (DatabaseName.key in jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName".keySet())
                         {
                            for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                            {
                                if(itemsSetting.key == 'DataBaseSSMS')
                                {
                                    def DBString = itemsSetting.value
                                    
                                    def DBStringSplitData = DBString.split(';')
                                    def keyValuePairs = [:]
                                    
                                    DBStringSplitData.each { pair ->
                                        def keyValue = pair.split('=')
                                        def key = keyValue[0].trim()
                                        def value = keyValue[1].trim()
                                        keyValuePairs[key] = value
                                    }
                                    
                                    keyValuePairs['Server'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['User ID'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Database'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
                                    
                                    
                                    def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                    
                                    jsonAppSetting."ConnectionStrings"."$targetField"."DataBaseSSMS" = data
                                    
                                    
                                }
                                else if(itemsSetting.key == 'SQLConnSP')
                                {
                                    def DBString = itemsSetting.value
                                    
                                    def DBStringSplitData = DBString.split(';')
                                    def keyValuePairs = [:]
                                    
                                    DBStringSplitData.each { pair ->
                                        def keyValue = pair.split('=')
                                        def key = keyValue[0].trim()
                                        def value = keyValue[1].trim()
                                        keyValuePairs[key] = value
                                    }
                                    
                                    keyValuePairs['Data Source'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['user id'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
                                    
                                    
                                    def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                    
                                    jsonAppSetting."ConnectionStrings"."$targetField"."SQLConnSP" = data
                                    
                                }
                            }
                         
                        }
                     }
                     else
                     {
                         if (DatabaseName.key in jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName".keySet())
                         {
                            for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                            {
                                if(itemsSetting.key == 'DataBase')
                                {
                                    def DBString = itemsSetting.value
                                    
                                    def DBStringSplitData = DBString.split(';')
                                    def keyValuePairs = [:]
                                    
                                    DBStringSplitData.each { pair ->
                                        def keyValue = pair.split('=')
                                        def key = keyValue[0].trim()
                                        def value = keyValue[1].trim()
                                        keyValuePairs[key] = value
                                    }
                                    
                                    keyValuePairs['Server'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['User ID'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Database'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
                                    
                                    
                                    def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                    
                                    jsonAppSetting."ConnectionStrings"."$targetField"."DataBase" = data
                                    
                                    
                                }
                                else if(itemsSetting.key == 'SQLConnSP')
                                {
                                    def DBString = itemsSetting.value
                                    
                                    def DBStringSplitData = DBString.split(';')
                                    def keyValuePairs = [:]
                                    
                                    DBStringSplitData.each { pair ->
                                        def keyValue = pair.split('=')
                                        def key = keyValue[0].trim()
                                        def value = keyValue[1].trim()
                                        keyValuePairs[key] = value
                                    }
                                    
                                    keyValuePairs['Data Source'] = jsonConfSetting."Database"."DataBaseSSMS"."Server"
                                    keyValuePairs['user id'] = jsonConfSetting."Database"."DataBaseSSMS"."User ID"
                                    keyValuePairs['Password'] = jsonConfSetting."Database"."DataBaseSSMS"."Password"
                                    keyValuePairs['Initial Catalog'] = jsonConfSetting."Database"."DataBaseSSMS"."DatabaseName"."$targetField"
                                    
                                    
                                    def data = keyValuePairs.collect { key, value -> "$key=$value" }.join(';')
                                    
                                    jsonAppSetting."ConnectionStrings"."$targetField"."SQLConnSP" = data
                                    
                                }
                            }
                         
                        }
                     }
                 
                 }
                 
                }
                
                
                //Ubah URL
                for(BeUrlName in jsonAppSetting."ConnectionStrings") {
                 if (BeUrlName.key in jsonConfSetting."URL".keySet())
                 {                     
                    targetField = BeUrlName.key;
                     
                    for(itemsSetting in jsonAppSetting."ConnectionStrings"."$targetField")
                    {
                        if(itemsSetting.key == 'URL')
                        {
                            def UrlString = jsonConfSetting."URL"."$targetField"
                            jsonAppSetting."ConnectionStrings"."$targetField"."URL" = UrlString
                        }
                    }
                     
                 }
                }
                
                
                //Ubah Connection String Redis
                jsonAppSetting."RedisConfig"."ConnStringRedis" = jsonConfSetting."Redis"."ConnStringRedis"
                
                // Memperbarui nilai field tertentu
                //json."test"."LogLevel"."$targetField" = newValue
                
                // Mengubah JSON kembali menjadi string
                data = JsonOutput.toJson(jsonAppSetting)
            }
            
            Map configData = [(config.configMapFileName): data]
            configmap.data = configData


            sh "rm ./configmap.yaml"
            writeYaml(data: configmap, file: "configmap.yaml")

            sh "type ./deployment.yaml"
            sh "type ./service.yaml"
            sh "type ./configmap.yaml"
            
            //def deployment2 = new File('./deployment.yaml')
            //def deployment2 = readYaml(file: 'deployment.yaml')
            //def datadeployment = deployment2.text
                
            //def service2 = new File('./service.yaml')
            //def service2 = readYaml(file: 'service.yaml')
            //def dataservice = service2.text
                
            //def configmap2 = new File('./configmap.yaml')
            //def configmap2 = readYaml(file: 'configmap.yaml')
            //def dataconfigmap = configmap2.text
            
            // Gabungkan konten kedua file menjadi satu teks
            //def merged_data = "${deployment}\n ------- \n${service}\n ------- \n${configmap}"

            // Konversi teks gabungan menjadi objek YAML
            //def yaml = new Yaml()
            //def obj = yaml.load(merged_data)

            // Simpan objek gabungan ke dalam file baru
            writeYaml(datas: [deployment, service, configmap], file: "deploymentservice.yaml")
            //def output = new File('deploymentservice.yaml')
            //yaml.dump(obj, output.newWriter())
                
            sh "type ./deploymentservice.yaml"
            
            }
        } else {
            configFileProvider([configFile(fileId: 'kube-deployment-yaml', targetLocation: './deployment.yaml', variable: 'deployment'), configFile(fileId: 'kube-service-yaml', targetLocation: './service.yaml', variable: 'service'), configFile(fileId: 'kube-configmap-yaml', targetLocation: './configmap.yaml', variable: 'configmap')]) {
            def matchers = ~ /.*-(frontend|fe)/
            config.type = config.type ? config.type: (matchers.matcher(config.deploymentName).matches() ? "fe": "be")

            // Namespace
            Map namespace = [apiVersion: "v1", kind: "Namespace", metadata: [name: config.namespace]]
            if (!fileExists('namespace.yaml')) {
                writeYaml(data: namespace, file: "namespace.yaml")
            }

            // Deployment
            def deployment = readYaml(file: 'deployment.yaml')
            deployment.metadata.name = config.deploymentName
            deployment.metadata.namespace = config.namespace
            deployment.metadata.labels.app = config.deploymentName
            deployment.spec.selector.matchLabels.app = config.deploymentName
            deployment.spec.template.metadata.labels.app = config.deploymentName
            deployment.spec.template.spec.volumes[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.volumes[0].configMap.name = """${config.deploymentName}-appsettings"""
            deployment.spec.template.spec.containers[0].name = config.deploymentName
            deployment.spec.template.spec.containers[0].image = config.imageName
            deployment.spec.template.spec.containers[0].volumeMounts[0].name = """${config.deploymentName}-volume"""
            deployment.spec.template.spec.containers[0].volumeMounts[0].mountPath = (config.configContainerPath ? config.configContainerPath: (config.type == 'fe' ? "/usr/share/nginx/html/assets/config/${config.configMapFileName}": "/app/${config.configMapFileName}"))
            deployment.spec.template.spec.containers[0].volumeMounts[0].subPath = config.configMapFileName

            bat "del deployment.yaml"
            writeYaml(data: deployment, file: "deployment.yaml")

            // Service
            def service = readYaml(file: 'service.yaml')
            service.metadata.name = config.deploymentName
            service.metadata.namespace = config.namespace
            service.spec.selector.app = config.deploymentName
            service.spec.ports[0].port = config.port
            service.spec.ports[0].targetPort = config.targetPort
            service.spec.type = config.serviceType

            bat "del service.yaml"
            writeYaml(data: service, file: "service.yaml")

            // ConfigMap
            def configmap = readYaml(file: 'configmap.yaml')
            configmap.metadata.name = """${config.deploymentName}-appsettings"""
            configmap.metadata.namespace = config.namespace
            def data = config.configPath ? readFile(config.configPath): "{}"
            Map configData = [(config.configMapFileName): data]
            configmap.data = configData


            bat "del configmap.yaml"
            writeYaml(data: configmap, file: "configmap.yaml")

            bat "type deployment.yaml"
            bat "type service.yaml"
            bat "type configmap.yaml"
           }
        }
}
