-- CG-DOC: File for the form service: V4__add_attachment_to_survey.
-- Add attachment_path to health_surveys
ALTER TABLE health_surveys ADD COLUMN attachment_path TEXT;
