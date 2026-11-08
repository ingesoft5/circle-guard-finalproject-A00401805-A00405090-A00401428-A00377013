-- CG-DOC: Database migration V3__extend_building_coordinates for the promotion service.
-- Extend buildings with coordinates and address
ALTER TABLE buildings ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE buildings ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE buildings ADD COLUMN address TEXT;
