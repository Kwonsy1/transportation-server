-- Transportation Server Database Schema

-- Subway Stations Table
CREATE TABLE IF NOT EXISTS subway_stations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    line_number VARCHAR(20) NOT NULL,
    station_code VARCHAR(20) UNIQUE,
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    address VARCHAR(255),
    external_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Subway Schedules Table
CREATE TABLE IF NOT EXISTS subway_schedules (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL,
    direction VARCHAR(20) NOT NULL,
    day_type VARCHAR(20) NOT NULL, -- weekday, saturday, sunday
    departure_time TIME NOT NULL,
    arrival_time TIME,
    end_station VARCHAR(100),
    train_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (station_id) REFERENCES subway_stations(id) ON DELETE CASCADE
);

-- Subway Exits Table
CREATE TABLE IF NOT EXISTS subway_exits (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL,
    exit_number VARCHAR(10) NOT NULL,
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    address VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (station_id) REFERENCES subway_stations(id) ON DELETE CASCADE
);

-- Bus Routes Table
CREATE TABLE IF NOT EXISTS bus_routes (
    id BIGSERIAL PRIMARY KEY,
    exit_id BIGINT NOT NULL,
    route_number VARCHAR(20) NOT NULL,
    route_name VARCHAR(100),
    bus_type VARCHAR(20), -- 일반, 급행, 마을버스 등
    start_station VARCHAR(100),
    end_station VARCHAR(100),
    first_bus_time VARCHAR(10),
    last_bus_time VARCHAR(10),
    interval_minutes INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exit_id) REFERENCES subway_exits(id) ON DELETE CASCADE
);

-- Exit Facilities Table
CREATE TABLE IF NOT EXISTS exit_facilities (
    id BIGSERIAL PRIMARY KEY,
    exit_id BIGINT NOT NULL,
    facility_name VARCHAR(100) NOT NULL,
    facility_type VARCHAR(50), -- 편의점, 카페, 병원, 은행 등
    distance_meters INTEGER,
    walking_minutes INTEGER,
    address VARCHAR(255),
    phone_number VARCHAR(20),
    operating_hours VARCHAR(100),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exit_id) REFERENCES subway_exits(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_subway_stations_name ON subway_stations(name);
CREATE INDEX IF NOT EXISTS idx_subway_stations_line ON subway_stations(line_number);
CREATE INDEX IF NOT EXISTS idx_subway_stations_code ON subway_stations(station_code);
CREATE INDEX IF NOT EXISTS idx_subway_stations_location ON subway_stations(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_subway_schedules_station ON subway_schedules(station_id);
CREATE INDEX IF NOT EXISTS idx_subway_exits_station ON subway_exits(station_id);
CREATE INDEX IF NOT EXISTS idx_bus_routes_exit ON bus_routes(exit_id);
CREATE INDEX IF NOT EXISTS idx_exit_facilities_exit ON exit_facilities(exit_id);