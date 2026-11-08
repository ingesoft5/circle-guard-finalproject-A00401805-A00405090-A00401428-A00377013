-- CG-DOC: File for the form service: V3__add_responses_to_survey.
-- Add responses to health_surveys
ALTER TABLE health_surveys ADD COLUMN responses JSONB;
