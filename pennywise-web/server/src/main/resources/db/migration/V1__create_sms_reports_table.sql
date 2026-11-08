-- Create sms_reports table for collecting user feedback on SMS parsing
CREATE TABLE IF NOT EXISTS sms_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    parsed_result JSONB,
    user_expected JSONB NOT NULL,
    user_note TEXT,
    reviewed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_sms_reports_reviewed ON sms_reports(reviewed);
CREATE INDEX idx_sms_reports_created_at ON sms_reports(created_at DESC);
CREATE INDEX idx_sms_reports_sender_id ON sms_reports(sender_id);

-- Add comment to table
COMMENT ON TABLE sms_reports IS 'User-reported SMS messages that were not parsed correctly or not supported';
COMMENT ON COLUMN sms_reports.parsed_result IS 'What our parser detected (null if not parsed)';
COMMENT ON COLUMN sms_reports.user_expected IS 'What the user says it should parse to';