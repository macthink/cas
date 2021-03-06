package org.apereo.cas.web;

import org.apereo.cas.web.support.CookieRetrievingCookieGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * Generates the warning cookie.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
public class WarningCookieRetrievingCookieGenerator extends CookieRetrievingCookieGenerator {

    /**
     * Instantiates a new warning cookie retrieving cookie generator.
     *
     * @param name     cookie name
     * @param path     cookie path
     * @param maxAge   cookie max age
     * @param secure   if cookie is only for HTTPS
     * @param httpOnly the http only
     */
    public WarningCookieRetrievingCookieGenerator(final String name, final String path,
                                                  final int maxAge, final boolean secure,
                                                  final boolean httpOnly) {
        super(name, path, maxAge, secure, null, httpOnly);
    }
}
