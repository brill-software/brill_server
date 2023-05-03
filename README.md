# Brill Enterprise Server

#### TODO list


1. Image display for confluence pages
2. Process confluence page as XML
3. Remove old material_ui_lib components
3. Remove hard coding of database on the server
4. CMS - select application
5. CMS Edit page - initial with drag and drop
6. CMS save of page
7. 
8. 
9. 
10. 
11. 

## Licenses

The Brill Framework (Client, Middleware and Server) is distributed under the MIT license. See the LICENSE file.

The MIT license is a widely used license that is a short simple permissive license with conditions only requiring preservation of copyright and 
license notices. Licensed works, modifications, and larger works may be distributed under different terms and without source code.

You may wish to mark any code modification you make with your own copyright and license details. For example:

// Original: © 2021 Brill Software Limited - Brill Framework, distributed under the MIT license.
// Modifications: © 2021 Trading Enterprises Inc. - Trader project, covered by the Trader Enterprices Inc. Proprietary license.

You don't need to identify the exact lines of code that were changed. These can be identified using Git and a differences tool.

New modules and files that you create only need to contain your own copyright message.

It would be appreciated if you were able to make any generic fixes and changes under the MIT license, so that they could be 
included into future releases of the Brill Framework.

## Git Repository

### Cloning the repository

Before cloning the repository you will need to create a public/private key pair in ~/.ssh and add the private
key to Git Bucket to handle authentication. Use of a username and password is not supported.

See https://confluence.atlassian.com/bitbucket/set-up-an-ssh-key-728138079.html for details on creating the keypair and adding the
private key to Git Bucket.

You can then clone the repository with:

`git clone git@bitbucket.org:brillsoftware/brill_enterprise_server.git`

## Respository private key OPENSSH not supported ( MacOS only )

On MacOS the public/private key format need to be converted from OPENSSH format to RSA format.

Check that `~/.ssh/id_rsa` starts wtih:

`-----BEGIN OPENSSH PRIVATE KEY-----`

To convert to RSA format use the command:

`ssh-keygen -p -f id_rsa -m pem -P "<passphrase>" -N "<passphrase>"`

Use a passphrase of `""` to set no passphrase. Setting of a passphrase is not supported.

After converting the private key check that the `~/.ssh/id_rsa` starts with:

`-----BEGIN RSA PRIVATE KEY-----`


git rm --cached "brill_server/bin/main/brill/server/service/GitService*.class" 

### Git branching strategy

See https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow

To merge develop into master and create a tagged release:

```
git checkout master
git merge develop
git push
git tag -a v0.1 -m "Version 0.1"
git push origin v0.1
```

## Brill Server Configuration

### Spring boot profile

The spring boot profile environment variable is set in `.vscode/launch.json` or can be passed as a command line parameter.


* `local` - Local workstation for development purposes
* `dev` - Development environment
* `int` - Integration environment
* `acceptance` - Acceptance environment
* `pref` - Performance testing environment
* `uat` - User Acceptance Test environment
* `prod` - Production

Mutiple profiles can be specified. e.g. `local,test`.

### Application.yml file

All app and configuration data is held in a Git repository. On startup the Brill Server needs to know the location of the remote and local Git repositories. This is held in the application.yml file.

The brill.apps git repo holds configuration details of the applications and configuration information for accessing and querying the database.

This is an example of a section of the application.yml file to configure the local environment profile.

```
---
spring:
   profiles: local
   application:
      name: brillserver
server:
   port: 8080
brill.apps:
    repo: git@bitbucket.org:brillsoftware/brill_demo_app.git
    local.repo.dir: /Users/chrisbulcock/BrillAppsRepo
    skip.repo.pull: true

logging:
    level:
        web: INFO
        brill: TRACE
```

#### Property brill.apps.repo.git

This is the location of the Git Bucket repository that holds the apps configuration data. Only SSH access is supported and this requires a public and private key to exist in the `~/.ssh` directory. HTTPS is not supported as this is less secure and would require configuration of a username and password.

#### Property brill.apps.local.repo.dir

This is the directory to store a local copy of the repository. One start up of the Brill Server for the first time, the repository is cloned from the remote repository. On subsequent startups a `git pull` is performed.

#### Property brill.apps.skip.repo.pull

The "git pull" on startup can be turned off by setting the `brill.apps.skip.repo.pull` property to `true`. This is useful for local development, where the server is fequently restarted and a fast restart is useful. It can also be used to work offline when there's internet connection for accessing the remote git repository.


#### Running the spring boot process

Either use the Visual Code Spring-boot-dashboard plug-in or run from the command line.

```
./gradlew bootRun
```

The profile is set in build.graddle. For example to set dev as the profile:

```
bootRun {
    systemProperty "spring.profiles.active", "dev"
}
```

### Production build

#### Building the JAR

```
 ./gradlew clean build
```

```
java -jar -Dspring.profiles.active=prod  build/libs/brill_server-0.0.1-SNAPSHOT.jar
```


#### Generate self signed certificate

```
keytool -genkey -alias selfsigned_localhost_sslserver -keyalg RSA -keysize 2048 -validity 700 -keypass changeit -storepass changeit -keystore ssl-server.jks
```

#### Generating a CSR

brillserver@brill1 BrillServer % keytool -genkey -keysize 2048 -keyalg RSA -alias tomcat -keystore brill_2021_keystore.jks
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
brillserver@brill1 BrillServer % 

```
 keytool -certreq -alias tomcat -file wwwbrillsoftware.csr -keystore brill_keystore.jks
```


#### Running the Production JAR

Create a user called brillserver

su brillserver
cd
mkdir BrillServer

Copy the Build Jar file to BrillServer.

Create brill.server.plist in /Library

Put the git access credentials into the .ssh directory

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

Re-boot the machine.

### Stoping and restarting the Server

sudo launchctl unload /Library/LaunchDaemons/brill.server.plist

sudo launchctl load /Library/LaunchDaemons/brill.server.plist

### Removing file from git

Sometimes files get committed to git when they shouldn't be. To remove a file from git use:

```
git rm --cached <fileToRemove>
```

## WebSockets and the Brill subscribe/publish protocol

### Overview
All communication between the Client and Server is using WebSockets. With REST the Client sends a request using a
GET, PUT or POST and recevies back a response with a HTTP Status code. A TCP/IP connection is opened and closed for 
each REST request. With WebSockets the connection remains continuously open and
is bi-directional. The Server can send messages to the Client at any time and vice versa. There are no request/response
message pairs. The Server could say send 10 messages to the Client and not require any response from the Client.

WebSockets allows the Server to notify interested Clients of changes to Server data, without the need for polling. 
It simplifies the Client and Server code. There's no need for the Server to produce an immediate response to a request and
the Client code doesn't have to wait for the reponse. This fits in well with the JavaScript / React approach of using
callbacks to process messages when they are received.


### Overview
The Brill Client application implements a Message Broker that maintains a list of subscriptions. A WebScoket
is used as a bridge to the Server. Whenever there's a subsciption to a Topic that starts with a "/" character, the
subscription is passed to the Server. The Server maintains a git repository of Topics and Content. Should the Server
receive a 


### Topics, Subscriptions and Publishers
Think of a Topic as as the name of something that you're interested in and want to subscribe to. Take the example
of a magazine. You might subscribe to the "Crane World" magazine. You send a Subscription request to the Publisher via 
the Post Office (a Message Broker). When the Publisher receives your Subscription request they post back a copy of the latest
magazine. Next month when the new edition comes out, the Publisher sends you the new edition. This would carry on 
each month until you cancel your subscription or stop the credit card payments.

Topic: The name of the item of interest. A Topic starting with a "/" is briged to the Server, others just stay on the Client. For the "Crane World" we need to use the Post Office as a bridge to the Publisher and therefore the Topic will start with a "/".

Content: The data, message or value that corresponds to the Topic. Or the "Crane World" magazine.

Subscribe event: A notification to the Message Broker that the Client wants to know the Topic. The Client will provide a callback function to be called with the first edition of the content and called again when a Publisher updates the content. 

Publish event: An update to a Topic with new content. Any Subscribers will be notified of the update.

Unscubscribe event: A notification to the Message Broker that the Client is no longer interested in the Topic. Very important as you don't want end up with piles of unwanted magazies.

Authenticate event: We can't allow jut anyone to subscribe to any Topic or worse publish to any Topic. We need to know who they are and what their priveleges are. You need an account and to pay to receive "Crane World".

Topic Extension: Topics are like file names where the last part after the dot indicates the Content type. e.g. A Topic of "/crane_world/magazine.json" has JSON content. 

The Message Broker and Server use JSON most of the time. When content is not of type JSON, actions are taken to produce JSON from it. With JavaScript (.js) content, the Server executes the JavaScript to produce JSON. Various checks are required before running the JavaScript on the Server
and the JavaScript runs in a Sandbox. The result is JSON that's published.

Sometimes a Topic might have a very large amount of content and the Client only wants a small portion. To restrict the content, a subscription can include a Filter. The Filter limits
the content to that of interest.





.js Only execute is filter contains {exeute=true}
if (execute is false, return the content base 64 encoded)


ws://localhost:8080/brill_ws
{"event": "subscribe", "topic": "/app.json"}
{"event": "subscribe", "topic": "/app.jsonc"}
{"event": "subscribe", "topic": "/app.jsonc", "filter": {"base64": true}}
{"event": "subscribe", "topic": "/database/employee/readPage.sql", "filter": {"sortCol": "first_name", "sortDirection": "asc", "offset": 0, "row_count": 5}}

{"event": "publish", "topic": "/app.json", "content": {"base64": "ewogICAgImFwcERlc2NyaXAiOiAiQnJpbGwgQ01TIiwKICAgICJsb2dpblBhZ2UiOiAiL2xvZ2luIiwKICAgICJob21lUGFnZSI6ICIvaG9tZSIsCiAgICAibm90Rm91bmRQYWdlIjogIi9lcnJvcllZWSIKICB9Cg=="}}