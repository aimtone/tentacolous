package io.github.aimtone.tentacolous.it;

import io.github.aimtone.tentacolous.annotations.TentacolousCapture;
import org.springframework.context.annotation.Configuration;

@Configuration
@TentacolousCapture(entity = Person.class)
class PersonCaptureConfig {
}
