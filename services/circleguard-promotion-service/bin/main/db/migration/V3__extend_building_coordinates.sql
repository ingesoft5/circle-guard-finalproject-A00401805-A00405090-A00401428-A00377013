-- CG-DOC: File for the promotion service: V3__extend_building_coordinates.
-- Extend buildings with coordinates and address
ALTER TABLE buildings ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE buildings ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE buildings ADD COLUMN address TEXT;
