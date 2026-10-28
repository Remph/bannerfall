package de.bannerfall.tutorial;

/** One line of the tutorial's list ("All steps"). */
public record TutorialStepDto(
        TutorialStep key, TutorialStep.Kind kind, TutorialStepStatus status) {}
