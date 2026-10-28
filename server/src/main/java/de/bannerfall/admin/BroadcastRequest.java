package de.bannerfall.admin;

/**
 * POST /api/admin/broadcast (a popup) and POST /api/admin/chat (a chat message). Checked like a
 * chat message ({@code ChatService}): 1–500 characters after trimming.
 */
public record BroadcastRequest(String text) {}
