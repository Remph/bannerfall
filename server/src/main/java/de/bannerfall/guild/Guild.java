package de.bannerfall.guild;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A guild (02-game-rules §16). Members: {@link GuildMember}. */
@Entity
public class Guild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique ignoring case (index {@code guild_name_lower}). */
    private String name;

    /** Unique ignoring case (index {@code guild_tag_lower}). */
    private String tag;

    private String description;

    private Instant createdAt;

    protected Guild() {}

    public Guild(String name, String tag, Instant createdAt) {
        this.name = name;
        this.tag = tag;
        this.description = "";
        this.createdAt = createdAt;
    }

    public void describe(String newDescription) {
        this.description = newDescription;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
