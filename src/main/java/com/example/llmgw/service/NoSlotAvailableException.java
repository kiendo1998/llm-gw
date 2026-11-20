package com.example.llmgw.service;

public class NoSlotAvailableException extends RuntimeException {
    public NoSlotAvailableException() { super("All LLM slots are busy"); }
}
