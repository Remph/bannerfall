package de.bannerfall.simulator;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/simulator (04-api): the attack simulator of the rally point (M23). For logged-in
 * players like the rest of the API; it reads no village, so there is nothing to own.
 */
@RestController
@RequestMapping("/api/simulator")
class SimulatorController {

    private final SimulatorService simulator;

    SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @PostMapping
    SimulationDto simulate(@Valid @RequestBody SimulatorRequest body) {
        return simulator.simulate(body);
    }
}
