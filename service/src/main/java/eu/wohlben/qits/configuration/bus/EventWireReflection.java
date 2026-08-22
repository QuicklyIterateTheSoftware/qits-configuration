package eu.wohlben.qits.configuration.bus;

import eu.wohlben.qits.ci.events.SoftwareRelease;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * What crosses the event wire into this service, told to native-image. No code, no bean, nothing at
 * runtime: the annotation is the whole content, and this class exists so the annotation has
 * somewhere to live that can say why.
 *
 * <p><b>Why it is not found automatically.</b> The eventstream jar canonicalises and binds JSON with
 * an {@code ObjectMapper} it builds by hand — the canonical form is a wire contract compared
 * byte-for-byte, so it must not be downstream of any application's customizer. To the build step
 * scanning for what needs reflecting on, that mapper and everything it touches are invisible. The
 * measured cost of the omission, on qits-ci's deployed binary, was Jackson's {@code No serializer
 * found for class … native image, you may need to configure reflection} — a record with no
 * reflection metadata has no components to find. See qits-ci's {@code EventWireReflection} for the
 * full account.
 *
 * <p><b>Why only {@link SoftwareRelease}.</b> It is the one event this service consumes. The
 * envelope and frame types the bus itself binds — {@code EventFrame}, {@code EventPage} — carry their
 * own {@code @RegisterForReflection} inside the eventstream jar, and this service publishes nothing,
 * so no envelope or mix-in registration belongs here. A listener for a second event type adds its
 * class to this list in the same commit.
 */
@RegisterForReflection(targets = {SoftwareRelease.class})
public final class EventWireReflection {

  private EventWireReflection() {}
}
