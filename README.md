# Brill Server

The Brill Server is part of the Brill Framework. The Brill Server is a Spring Boot application
written in Java, that runs on a server machine. The Brill Server communications with Brill Clients
using the [Brill Middleware](https://brill.software/brill_software/middleware "Brill Middleware").


## Git Repository

The master copy of the Brill Server project is kept at 

- Bitbucket (git@bitbucket.org:brill-software/brill_server.git)

The project is also available from:

- Sourceforce (git://git.code.sf.net/p/brill-software/brill_server)
- GitLab (git@gitlab.com:brill-software/brill_server.git)
- GitHub (git@github.com:brill-software/brill_server.git)

To make changes, you either need permission to write to the Bitbucket repository or create a fork repository.
You can create a fork repository on Bitbucket, Sourceforge, GitLab, GitHub, AWS CodeCommit or your own Git Server.

## Brill Server Configuration

The Brill Server acts as a web server and listens for HTTP requests on a port. When the Brill Server starts, a Spring
Boot Profile is passed into the process. This Spring Boot Profile is used to get the HTTP port number and other configuration
parameters from the **application.yml** file.

### Spring boot profile

The Spring Boot Profile environment variable is set in `.vscode/launch.json` or can be passed as a command line parameter.

The Spring Boot Profile can be one of:

* `local` - Local workstation for development purposes
* `prod` - Production

Other Profiles can be defined. For example `dev`, `test`, `integration`.

### Application.yml file

All configuration values are held in the `/src/resources/application.yml` file

This is an example of a section of the **application.yml** file to configure for the **local** Spring Boot profile.

```
---
spring:
   profiles: local
   application:
      name: brill_server
server:
   port: 8080
   allowedOrigins: "*"
   botsWebsite: true
   sessionsDirectory: sessions
database:
   driver: com.mysql.cj.jdbc.Driver
   url: jdbc:mysql://localhost:3306/user_db
   username: ${BRILL_LOCAL_DATABASE_USERNAME}
   password: ${BRILL_LOCAL_DATABASE_PWD}
brill.apps:
   repo: git@bitbucket.org:brill-software/brill_apps_fork.git
   local.repo:
      dir: ../brill_workspace
      skip.pull: false
passwords.pepper: ${BRILL_LOCAL_PWDS_PEPPER}
passwords.allowClearText: true
logging:
    level:
        web: INFO
        brill: TRACE
```

Note that sensitive values such as the database username and password are passed in as operating system environment variables. These need to
be configured in the startup script, shell or terminal configuration file.

In Visual Studio Code, use the mouse to hover over each of the values in the **application.yml** file to see a description.

See the [Brill Software Developer Guide](https://www.brill.software/brill_software/developers_guide "Developers Guide") for more details..

#### Running the spring boot process

Either use the Visual Code Spring-boot-dashboard plug-in or run from the command line.

```
./gradlew bootRun
```

The profile is set in build.graddle. For example to set dev as the profile:

```
bootRun {
    systemProperty "spring.profiles.active", "local"
}
```

### Production build

Before building the Brill Server, the Brill Client must be built first. The Brill Client build process creates a
build driectory and subdirectories that contain HTML pages, JavaScript and other resource files. The Brill Client
build directory has to be copied over to Brill Server static content directory. The Brill Server acts as a Web Server 
and also handles WebSocket connections on the same port number.

#### Building the JAR

```
 ./gradlew clean copyWebApp build
```

To run the process from the command line use:

```
java -jar -Dspring.profiles.active=prod  build/libs/brill_server-0.0.1-SNAPSHOT.jar
```

#### Generateing a Self Signed Certificate

Normally the **local** profile is configured to use *HTTP* and the **prod** profile to use *HTTPS*. For *HTTPS*, a Digital Certificate is 
required that was signed by a Certification Authority. For initial setup and test purposes, a self signed certificate can be used. A
self signed certificate can be generated as follows:

```
keytool -genkey -alias selfsigned_localhost_sslserver -keyalg RSA -keysize 2048 -validity 700 -keypass changeit -storepass changeit -keystore ssl-server.jks
```

#### Generating a CSR

To get a proper CA signed Digital Certificate you will need to contact your domain name provider or a Certification Authority. Follow their
instructions. Typically they will require a CSR that is generated on the Server Machine. The following is an example of generating a CSR:

```
% keytool -genkey -keysize 2048 -keyalg RSA -alias tomcat -keystore brill_keystore.jks
Enter keystore password:  
Re-enter new password: 
What is your first and last name?
  [Unknown]:  brill.software
What is the name of your organizational unit?
  [Unknown]:  Support
What is the name of your organization?
  [Unknown]:  Brill Software Limited
What is the name of your City or Locality?
  [Unknown]:  Dublin
What is the name of your State or Province?
  [Unknown]:  Ireland
What is the two-letter country code for this unit?
  [Unknown]:  IE
Is CN=brill.software, OU=Support, O=Brill Software Limited, L=Dublin, ST=Ireland, C=IE correct?
  [no]:  yes

Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 90 days
	for: CN=brill.software, OU=Support, O=Brill Software Limited, L=Dublin, ST=Ireland, C=IE

% keytool -certreq -alias tomcat -file wwwbrillsoftware.csr -keystore brill_keystore.jks
```

#### Running the Production JAR on MacOS

Create a user called **brillserver**. 

```
su - brillserver
mkdir BrillServer
```

Copy the Build Jar file to **BrillServer** directory.

Create a file called **brill.server.plist** in **/Library/LaunchDaemons**

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>             <string>brill.server</string>
    <key>ProcessType</key>       <string>Interactive</string>
    <key>Disabled</key>          <false/>
    <key>RunAtLoad</key>         <true/>
    <key>KeepAlive</key>
        <dict>
            <key>SuccessfulExit</key>
            <false/>
            <key>AfterInitialDemand</key>
            <true/>
        </dict>
    <key>SessionCreate</key>     <true/>
    <key>LaunchOnlyOnce</key>    <false/>
    <key>UserName</key>          <string>brillserver</string>
    <key>GroupName</key>         <string>staff</string>
    <key>ExitTimeOut</key>       <integer>600</integer>
    <key>EnvironmentVariables</key>
        <dict>
            <key>BRILL_PROD_SERVER_SSL_KEY_STORE</key>
            <string>brill_keystore.jks</string>
            <key>BRILL_PROD_SERVER_SSL_KEY_STORE_PWD</key>
            <string>changeme</string>
            <key>BRILL_PROD_DATABASE_USERNAME</key>
            <string>brillserver</string>
            <key>BRILL_PROD_DATABASE_PWD</key>
            <string>databasePwd</string>
            <key>BRILL_PROD_PWDS_PEPPER</key>
            <string>pepperString</string>
        </dict>
    <key>Program</key>           <string>/usr/bin/java</string>
    <key>ProgramArguments</key>
        <array>
            <string>/usr/bin/java</string>
            <string>-jar</string>
            <string>-Dspring.profiles.active=prod</string>
            <string>/Users/brillserver/BrillServer/brill_server-0.0.1.jar</string>
        </array>
    <key>WorkingDirectory</key>  <string>/Users/brillserver/BrillServer</string>
    <key>StandardErrorPath</key> <string>/Users/brillserver/BrillServer/log.err</string>
    <key>StandardOutPath</key> <string>/Users/brillserver/BrillServer/log.out</string>
</dict>
</plist>
```

Put the git access public and private key into the **.ssh** directory

Re-boot the machine. Check that the Brill Server starts. Access the system using a web browser and check the log file for errors.

### Stoping and restarting the Brill Server

```
sudo launchctl unload /Library/LaunchDaemons/brill.server.plist

sudo launchctl load /Library/LaunchDaemons/brill.server.plist
```

## Licensing

See the LICENSE file in the root directory and the [Brill Software website](https://www.brill.software "Brill Software") for more details.