-- CG-DOC: Database migration V4__add_attachment_to_survey for the form service.
-- Add attachment_path to health_surveys
ALTER TABLE health_surveys ADD COLUMN attachment_path TEXT;
