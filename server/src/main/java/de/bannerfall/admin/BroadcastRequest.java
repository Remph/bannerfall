package de.bannerfall.admin;

/**
 * POST /api/admin/broadcast. Checked like a chat message ({@code ChatService}): 1–500 characters
 * after trimming.
 */
public record BroadcastRequest(String text) {}
