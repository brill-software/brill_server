CREATE DATABASE  IF NOT EXISTS `brill_prod_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `brill_prod_db`;
-- MySQL dump 10.13  Distrib 8.0.29, for macos12 (x86_64)
--
-- Host: 127.0.0.1    Database: user_db
-- ------------------------------------------------------
-- Server version	8.0.19

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `brill_cms_permission`
--

DROP TABLE IF EXISTS `brill_cms_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brill_cms_permission` (
  `permission_id` int NOT NULL,
  `permission` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `brill_cms_permission`
--

LOCK TABLES `brill_cms_permission` WRITE;
/*!40000 ALTER TABLE `brill_cms_permission` DISABLE KEYS */;
INSERT INTO `brill_cms_permission` VALUES (1,'file_read'),(2,'file_write'),(3,'git_read'),(4,'git_write'),(5,'cms_user'),(6,'cms_developer'),(7,'cms_admin'),(8,'db_write'),(9,'chatbot');
/*!40000 ALTER TABLE `brill_cms_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `brill_cms_user`
--

DROP TABLE IF EXISTS `brill_cms_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brill_cms_user` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(45) NOT NULL,
  `first_name` varchar(45) NOT NULL,
  `last_name` varchar(45) NOT NULL,
  `email` varchar(45) NOT NULL,
  `password` varchar(512) DEFAULT NULL,
  `permissions` varchar(512) NOT NULL,
  `changePassword` varchar(1) NOT NULL,
  `last_login` datetime DEFAULT NULL,
  `deleted` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username_UNIQUE` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `brill_cms_user`
--

LOCK TABLES `brill_cms_user` WRITE;
/*!40000 ALTER TABLE `brill_cms_user` DISABLE KEYS */;
INSERT INTO `brill_cms_user` VALUES (1,'admin','Admin', 'User','noreply@none.com','Production','file_read,file_write,git_read,git_write,cms_user,change_workspace,cms_admin,db_write,chatbot','Y',NULL,'N');
/*!40000 ALTER TABLE `brill_cms_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ip_address`
--

DROP TABLE IF EXISTS `ip_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ip_address` (
  `ip_address_id` int NOT NULL AUTO_INCREMENT,
  `ip_address` varchar(45) NOT NULL,
  `country` varchar(45) DEFAULT NULL,
  `country_code` varchar(45) DEFAULT NULL,
  `region` varchar(45) DEFAULT NULL,
  `region_name` varchar(45) DEFAULT NULL,
  `city` varchar(45) DEFAULT NULL,
  `lat` decimal(8,5) DEFAULT NULL,
  `lon` decimal(8,5) DEFAULT NULL,
  `isp` varchar(100) DEFAULT NULL,
  `org` varchar(100) DEFAULT NULL,
  `ignore` varchar(1) DEFAULT 'N',
  PRIMARY KEY (`ip_address_id`,`ip_address`),
  UNIQUE KEY `ip_address_id_UNIQUE` (`ip_address_id`),
  UNIQUE KEY `ip_address_UNIQUE` (`ip_address`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci

DROP TABLE IF EXISTS `user_agent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_agent` (
  `user_agent_id` int NOT NULL AUTO_INCREMENT,
  `user_agent` varchar(512) NOT NULL,
  `os` varchar(45) DEFAULT NULL,
  `browser` varchar(45) DEFAULT NULL,
  `browser_version` varchar(45) DEFAULT NULL,
  `mobile` varchar(1) DEFAULT NULL,
  PRIMARY KEY (`user_agent_id`),
  UNIQUE KEY `user_agent_id_UNIQUE` (`user_agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci

--
-- Table structure for table `session_log`
--

DROP TABLE IF EXISTS `session_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session_log` (
  `session_log_id` int NOT NULL AUTO_INCREMENT,
  `session_id` varchar(45) NOT NULL,
  `start_date_time` datetime DEFAULT NULL,
  `end_date_time` datetime DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `ip_address_id` int DEFAULT NULL,
  PRIMARY KEY (`session_log_id`,`session_id`),
  UNIQUE KEY `session_id_UNIQUE` (`session_id`),
  UNIQUE KEY `session_log_id_UNIQUE` (`session_log_id`),
  KEY `ip_address_id_idx` (`ip_address_id`),
  CONSTRAINT `ip_address_id` FOREIGN KEY (`ip_address_id`) REFERENCES `ip_address` (`ip_address_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3661 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci

--
-- Table structure for table `session_page_log`
--

DROP TABLE IF EXISTS `session_page_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session_page_log` (
  `session_page_log_id` int NOT NULL AUTO_INCREMENT,
  `session_id` varchar(45) NOT NULL,
  `date_time` datetime DEFAULT NULL,
  `page` varchar(512) NOT NULL,
  PRIMARY KEY (`session_page_log_id`,`session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `session_log_view`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE VIEW `session_log_view` AS
    SELECT 
        `session_log`.`session_log_id` AS `session_log_id`,
        `session_log`.`session_id` AS `session_id`,
        `session_log`.`start_date_time` AS `start_date_time`,
        (UNIX_TIMESTAMP(`session_log`.`end_date_time`) - UNIX_TIMESTAMP(`session_log`.`start_date_time`)) AS `session_length`,
        `session_log`.`ip_address_id` AS `ip_address_id`,
        `ip_address`.`country` AS `country`,
        `ip_address`.`city` AS `city`,
        (SELECT 
                COUNT(0)
            FROM
                `session_log` `r`
            WHERE
                (`r`.`ip_address_id` = `session_log`.`ip_address_id`)) AS `visits`,
        (SELECT 
                COUNT(0)
            FROM
                `session_page_log` `pl`
            WHERE
                (`pl`.`session_id` = `session_log`.`session_id`)) AS `pages`,
        `ip_address`.`isp` AS `isp`,
        `ip_address`.`org` AS `org`
    FROM
        (`session_log`
        JOIN `ip_address`)
    WHERE
        ((`session_log`.`ip_address_id` = `ip_address`.`ip_address_id`)
            AND (`ip_address`.`ignore` <> 'Y'))
    ORDER BY `session_log`.`session_log_id` DESC


--
-- Table structure for table `employee`
--

DROP TABLE IF EXISTS `employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `employee` (
  `employee_id` int NOT NULL,
  `first_name` varchar(45) NOT NULL,
  `last_name` varchar(45) NOT NULL,
  `department` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`employee_id`),
  FULLTEXT KEY `TEXT` (`first_name`,`last_name`,`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `employee`
--

LOCK TABLES `employee` WRITE;
/*!40000 ALTER TABLE `employee` DISABLE KEYS */;
INSERT INTO `employee` VALUES (1,'Chris','Bulcock','Sports'),(2,'Albert','Williams','Pens'),(3,'Jane','Harding','Dresses'),(4,'Iris','O\'Connor','Food'),(5,'Willy','Whale','Baths'),(6,'Ali','Trotter','Food'),(7,'Walter','Smith','Food'),(8,'Joan','Clarke','IT'),(9,'Willy','Walsh','Admin'),(10,'Wilber','Force','Admin'),(11,'Bert','Large','Berverages'),(12,'Caron','Carter','HR'),(13,'Donald','Small','Admin'),(14,'Roland','Benito','Food'),(15,'Walter','White','Wine'),(16,'Gignesh','Patel','Food'),(17,'Alan','Turing','IT'),(18,'Buzz','Allen','Toys'),(19,'Julie','Moris','Lighting'),(20,'Peter','O\'Rourke','Cycles'),(21,'Willy','Black','Travel'),(22,'Grahame','Allan','Electrical'),(23,'Victor','Sparks','Electrical'),(24,'Tina','Turner','Piano'),(25,'Mary','FIsh','Food'),(26,'Karen','Walsh','Handbags'),(27,'Rajesh','Patel','Toys'),(28,'Dan','Hargreves','Plumbing'),(29,'Chalie','Church','Catering'),(30,'Sally','Collins','Food'),(31,'Susan','Carter','IT'),(32,'Elizabeth','Wheeler','HR'),(33,'Catherine','Head','Travel'),(34,'Tony','Fisher','IT'),(35,'Jannet','Williams','Accounts'),(36,'Grace','Harding','Hairdressing'),(37,'Terry','Smith','Accounts'),(38,'Daniel','Parker','Furniture'),(39,'Winny','Stone','Travel'),(40,'Liz','Chen','Food'),(41,'Gilbert','Miles','Accounts'),(42,'Michael','Burton','IT'),(43,'David','Mendip','Piano'),(44,'Stewart','Ranger','Toys'),(45,'Gillian','Stewart','Handbags');
/*!40000 ALTER TABLE `employee` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2023-05-09 16:47:52
