package eu.wohlben.qits.configuration;

import eu.wohlben.qits.archrules.DatasourceBaselineRules;
import org.junit.jupiter.api.Test;

/**
 * The `configuration` datasource carries the platform's resilience baseline: the patient driver,
 * validation at borrow, and a 15s acquisition timeout. The rule reads the config rather than the
 * code, and it names each missing line.
 *
 * <p>It lives in {@code service/} because this module's classpath is the deployable's whole config —
 * the datasource itself is declared in the {@code configuration} jar, and a service that adds a
 * second one is judged here without anything being added to this class.
 *
 * <p>A cutover of the tier's postgres is what the baseline exists for, and this service is deployed
 * by the component that performs it.
 */
class DatasourceBaselineTest {

  @Test
  void everyPostgresDatasourceCarriesTheBaseline() {
    DatasourceBaselineRules.assertBaseline();
  }
}
