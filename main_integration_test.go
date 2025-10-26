// +build integration

package main

import (
	"context"
	"testing"
	"time"
)

// TestIntegration_BasicContextOperation tests basic context operations
func TestIntegration_BasicContextOperation(t *testing.T) {
	ctx := context.Background()
	if ctx == nil {
		t.Error("Context should not be nil")
	}
}

// TestIntegration_ContextWithTimeout tests context with timeout
func TestIntegration_ContextWithTimeout(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()

	<-ctx.Done()
	if ctx.Err() != context.DeadlineExceeded {
		t.Errorf("Expected context.DeadlineExceeded, got %v", ctx.Err())
	}
}

// TestIntegration_ContextCancellation tests context cancellation
func TestIntegration_ContextCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	select {
	case <-ctx.Done():
		if ctx.Err() != context.Canceled {
			t.Errorf("Expected context.Canceled, got %v", ctx.Err())
		}
	default:
		t.Error("Context was not cancelled")
	}
}
