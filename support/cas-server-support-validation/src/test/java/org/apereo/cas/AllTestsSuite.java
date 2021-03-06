package org.apereo.cas;

import org.apereo.cas.web.ProxyControllerTests;
import org.apereo.cas.web.view.Cas10ResponseViewTests;
import org.apereo.cas.web.view.Cas20ResponseViewTests;
import org.apereo.cas.web.view.Cas30ResponseViewTests;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * The {@link AllTestsSuite} is responsible for
 * running all cas test cases.
 *
 * @author Misagh Moayyed
 * @since 4.2.0
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({Cas10ResponseViewTests.class, Cas20ResponseViewTests.class, Cas30ResponseViewTests.class,
    ProxyControllerTests.class})
@Slf4j
public class AllTestsSuite {
}

