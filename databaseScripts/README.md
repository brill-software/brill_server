# Brill Server Database Scripts

This directory contains scripts for initialising a new MySQL database.

## Scripts

To run a script:

- For a production database, change the passwords pepper value. See below for details.
- Login to MySQL Workbench.
- Check that the database specified in the script doesn't already exist.
- Select the Administration tab and Data Import/Restore.
- Select Import from Self-Contained File and select the script file.
- Click Start Import
- Log out and back in and check the imported database.
- Check the **application.yml** file specifies the correct DB.
- Start the Brill Server and login as **admin**.
- Change the **admin** password.

### MySQL_BrillLocalDb.sql

Sets up a database called brill_local_db with a user called **admin** and password **Development**.

### MySQL_BrillProdDb.sql

Sets up a database called brill_prod_db with a user called **admin** and password **Production**.

## Passwords pepper value

Before setting up a new production database, you might want to change the **passwords.pepper** 
value in the **application.yml** file. The pepper value makes a dictionary style attack on the
passowrd hashes more difficult and also prevents the copying of password hashes from one 
database to another database that has a different pepper value.

The pepper value can't be changed once a database contains hashed passwords, or at least not 
without invalidating the existing passwords.

## Database configuration

The database driver and location are held in the **application.yml** configuration file. 

For example:

> database:
>> driver: com.mysql.cj.jdbc.Driver
>> url: jdbc:mysql://localhost:3306/brill_prod_db    

In the example, the database schema name is **brill_prod_db**. This needs to match the schema specified in the script.

## Initial setup of user passwords

The brill_cms_user table password field contains either a hash of the password or the password as clear text. 

Clear text passwords are supported to allow the initial setup of a new user database.

With a clear text password, the user is forced to change their password on first login and therefore the
password will be hashed from then on.

After the initial setup of the user database, support for clear text passwords can be turned off by setting
the following in the application.yml configuration file:

> passwords.allowClearText: false


## Tables

### brill_cms_permissions table

Contains a list of the valid user permissions. i.e. file_read, file_write, git_read, git_write, cms_user and execute_sql.

### brill_cms_user table

Contains the CMS users. The columns are user_id, username, name, email, password, permissions, changePassword and deleted.

### employee table

The employee table has some example data that is used by some of the Storybook pages.

## Exporting a database

A new script can be created from a database using the Data Export tab in the MySQL Workbench.

## Links

[Brill Software](https://www.brill.software "Brill Software")

[Brill Software Developers Guide](https://www.brill.software/brill_software/developers_guide "Developers Guide")
