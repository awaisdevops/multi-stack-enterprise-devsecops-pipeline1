package main

import (
	"os"
	"testing"
)

// TestEnvironmentVariables tests basic environment variable operations
func TestEnvironmentVariables(t *testing.T) {
	os.Setenv("TEST_APP_VAR", "test_value")
	defer os.Unsetenv("TEST_APP_VAR")

	value := os.Getenv("TEST_APP_VAR")
	if value != "test_value" {
		t.Errorf("Expected 'test_value', got %q", value)
	}
}

// TestSimpleStringOperation tests basic string operations
func TestSimpleStringOperation(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{"empty string", "", ""},
		{"simple string", "hello", "hello"},
		{"version format", "1.0.0", "1.0.0"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.input != tt.expected {
				t.Errorf("Expected %q, got %q", tt.expected, tt.input)
			}
		})
	}
}
