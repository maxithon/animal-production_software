1. USERS & SECURITY
Table: user

CREATE TABLE user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    gender VARCHAR(10),
    national_id VARCHAR(16),
    email VARCHAR(100),
    phone VARCHAR(20),
    position VARCHAR(50), -- e.g. system admin, data clerk, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

Table: user_login

CREATE TABLE user_login (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(30) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    last_login TIMESTAMP,
    user_id INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id)
);

2. LOCATION MANAGEMENT

CREATE TABLE location (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(40) UNIQUE,
    parent_code VARCHAR(40),
    location_type VARCHAR(40), -- e.g. District, Sector, Cell, Village
    comments VARCHAR(255)
);

3. REPRESENTATIVE MANAGEMENT

CREATE TABLE representative (
    id INT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10),
    marital_status VARCHAR(20),
    national_id VARCHAR(16),
    phone VARCHAR(20),
    address VARCHAR(255),
    location_id INT,
    FOREIGN KEY (location_id) REFERENCES location(id)
);

4. CARETAKERS OF ANIMALS

CREATE TABLE caretaker (
    id INT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10),
    marital_status VARCHAR(20),
    national_id VARCHAR(16),
    phone VARCHAR(20),
    address VARCHAR(255),
    assigned_date DATE,
    representative_id INT,
    location_id INT,
    FOREIGN KEY (representative_id) REFERENCES representative(id),
    FOREIGN KEY (location_id) REFERENCES location(id)
);

5. ANIMAL TYPE

CREATE TABLE animal_type (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL
);

6. ANIMAL MAIN TABLE

CREATE TABLE animal (
    id INT PRIMARY KEY AUTO_INCREMENT,
    tag_number VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100),
    gender VARCHAR(10),
    breed VARCHAR(50),
    birth_date DATE,
    health_status VARCHAR(50),
    status VARCHAR(30) DEFAULT 'Active', -- Active, Dead, Sold, Transferred
    notes TEXT,
    animal_type_id INT,
    caretaker_id INT,
    location_id INT,
    FOREIGN KEY (animal_type_id) REFERENCES animal_type(id),
    FOREIGN KEY (caretaker_id) REFERENCES caretaker(id),
    FOREIGN KEY (location_id) REFERENCES location(id)
);

7. ANIMAL BREEDING (your itungo_rirymye)
CREATE TABLE animal_breeding (
    id INT PRIMARY KEY AUTO_INCREMENT,
    breeding_date DATE,
    method VARCHAR(100),
    expected_birth_date DATE,
    remarks VARCHAR(255),
    animal_id INT,
    FOREIGN KEY (animal_id) REFERENCES animal(id)
);

8. ANIMAL BIRTH (your ayabyaye)
CREATE TABLE animal_birth (
    id INT PRIMARY KEY AUTO_INCREMENT,
    date_of_birth DATE NOT NULL,
    offspring_count INT DEFAULT 1,
    remarks VARCHAR(255),
    parent_animal_id INT,
    FOREIGN KEY (parent_animal_id) REFERENCES animal(id)
);

9. ANIMAL DEATH (your amutungo_yapfuye)
CREATE TABLE animal_death (
    id INT PRIMARY KEY AUTO_INCREMENT,
    death_date DATE NOT NULL,
    cause_of_death VARCHAR(255),
    remarks TEXT,
    animal_id INT,
    FOREIGN KEY (animal_id) REFERENCES animal(id)
);

10. ANIMAL SALE (your amutungo_yo_kugurisha)
CREATE TABLE animal_sale (
    id INT PRIMARY KEY AUTO_INCREMENT,
    sale_date DATE NOT NULL,
    buyer_name VARCHAR(100),
    sale_price DECIMAL(12,2),
    remarks TEXT,
    animal_id INT,
    FOREIGN KEY (animal_id) REFERENCES animal(id)
);
11. ANIMAL TREATMENT (new but useful)
CREATE TABLE animal_treatment (
    id INT PRIMARY KEY AUTO_INCREMENT,
    treatment_date DATE NOT NULL,
    disease VARCHAR(100),
    medicine_used VARCHAR(100),
    veterinarian VARCHAR(100),
    remarks TEXT,
    animal_id INT,
    FOREIGN KEY (animal_id) REFERENCES animal(id)
);

12. LOGGING (as you requested)
CREATE TABLE system_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    action_type VARCHAR(50), -- CREATE, UPDATE, DELETE, LOGIN, LOGOUT
    description TEXT,
    table_name VARCHAR(100),
    record_id INT,
    performed_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performed_by) REFERENCES user(id)
);