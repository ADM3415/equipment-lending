-- MySQL dump 10.13  Distrib 8.4.13, for Win64 (x86_64)
--
-- Host: localhost    Database: equipment_lending
-- ------------------------------------------------------
-- Server version	8.4.13

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `invoices`
--

DROP TABLE IF EXISTS `invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` int NOT NULL,
  `charge_id` int NOT NULL,
  `issued_date` date NOT NULL,
  `is_sent` tinyint(1) NOT NULL DEFAULT '0',
  `last_send_attempt_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `charge_id` (`charge_id`),
  CONSTRAINT `invoices_ibfk_1` FOREIGN KEY (`charge_id`) REFERENCES `student_charges` (`id`),
  CONSTRAINT `invoices_chk_1` CHECK ((`id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `items`
--

DROP TABLE IF EXISTS `items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `items` (
  `id` int NOT NULL,
  `name` varchar(150) NOT NULL,
  `type` enum('LAPTOP','CAMERA','PROJECTOR') NOT NULL,
  `item_condition` enum('GOOD','PHYSICAL_DAMAGE','SOFTWARE_ISSUE','UNSPECIFIED_DAMAGE') NOT NULL,
  `storage_location` varchar(150) NOT NULL,
  `status` enum('IN_STOCK','ON_LOAN','LOST') NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `items_chk_1` CHECK ((`id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `loans`
--

DROP TABLE IF EXISTS `loans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `loans` (
  `id` int NOT NULL,
  `student_id` int NOT NULL,
  `item_id` int NOT NULL,
  `registered_by_id` int NOT NULL,
  `borrow_date` date NOT NULL,
  `due_date` date NOT NULL,
  `return_processed_by_id` int DEFAULT NULL,
  `returned_by_id` int DEFAULT NULL,
  `return_date` date DEFAULT NULL,
  `loss_reported_date` date DEFAULT NULL,
  `status` enum('ACTIVE','RETURNED','CLOSED_LOST') NOT NULL,
  `return_condition` enum('GOOD','PHYSICAL_DAMAGE','SOFTWARE_ISSUE','UNSPECIFIED_DAMAGE') DEFAULT NULL,
  `return_description` text,
  PRIMARY KEY (`id`),
  KEY `student_id` (`student_id`),
  KEY `item_id` (`item_id`),
  KEY `registered_by_id` (`registered_by_id`),
  KEY `return_processed_by_id` (`return_processed_by_id`),
  KEY `returned_by_id` (`returned_by_id`),
  CONSTRAINT `loans_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`),
  CONSTRAINT `loans_ibfk_2` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `loans_ibfk_3` FOREIGN KEY (`registered_by_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `loans_ibfk_4` FOREIGN KEY (`return_processed_by_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `loans_ibfk_5` FOREIGN KEY (`returned_by_id`) REFERENCES `members` (`id`),
  CONSTRAINT `loans_chk_1` CHECK ((`id` > 0)),
  CONSTRAINT `loans_chk_2` CHECK ((`due_date` >= `borrow_date`)),
  CONSTRAINT `loans_chk_3` CHECK (((`return_date` is null) or (`return_date` >= `borrow_date`))),
  CONSTRAINT `loans_chk_4` CHECK (((`loss_reported_date` is null) or (`loss_reported_date` >= `borrow_date`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `members`
--

DROP TABLE IF EXISTS `members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `members` (
  `id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(150) NOT NULL,
  `member_type` enum('STUDENT','STAFF','TEACHER') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `charge_id` int NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `payment_date` date NOT NULL,
  `receipt_reference` varchar(150) NOT NULL,
  `processed_by_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `charge_id` (`charge_id`),
  KEY `processed_by_id` (`processed_by_id`),
  CONSTRAINT `payments_ibfk_1` FOREIGN KEY (`charge_id`) REFERENCES `student_charges` (`id`),
  CONSTRAINT `payments_ibfk_2` FOREIGN KEY (`processed_by_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `payments_chk_1` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `staff`
--

DROP TABLE IF EXISTS `staff`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staff` (
  `id` int NOT NULL,
  `member_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `member_id` (`member_id`),
  CONSTRAINT `fk_staff_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `staff_chk_1` CHECK ((`id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_charges`
--

DROP TABLE IF EXISTS `student_charges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_charges` (
  `id` int NOT NULL,
  `loan_id` int NOT NULL,
  `type` enum('LATE_FEE','DAMAGE','LOSS') NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `description` text NOT NULL,
  `is_paid` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `loan_id` (`loan_id`),
  CONSTRAINT `student_charges_ibfk_1` FOREIGN KEY (`loan_id`) REFERENCES `loans` (`id`),
  CONSTRAINT `student_charges_chk_1` CHECK ((`id` > 0)),
  CONSTRAINT `student_charges_chk_2` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `students`
--

DROP TABLE IF EXISTS `students`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `students` (
  `id` int NOT NULL,
  `active` tinyint(1) NOT NULL,
  `university_email` varchar(254) NOT NULL,
  `member_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `university_email` (`university_email`),
  UNIQUE KEY `member_id` (`member_id`),
  CONSTRAINT `fk_students_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `students_chk_1` CHECK ((`id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teachers`
--

DROP TABLE IF EXISTS `teachers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teachers` (
  `id` int NOT NULL,
  `member_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `member_id` (`member_id`),
  CONSTRAINT `fk_teachers_member` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `teachers_chk_1` CHECK ((`id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 19:30:21
