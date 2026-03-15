package org.apache.fop.apps.io;

import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * InternalResourceResolver wrapper that guards null/blank URIs and avoids NPEs inside FOP.
 */
public final class NullSafeInternalResourceResolver extends InternalResourceResolver {

  private final InternalResourceResolver delegate;

  private NullSafeInternalResourceResolver(InternalResourceResolver delegate) {
    super(delegate.getBaseURI(), new DelegatingResourceResolver(delegate));
    this.delegate = delegate;
  }

  public static InternalResourceResolver wrap(InternalResourceResolver delegate) {
    if (delegate == null || delegate instanceof NullSafeInternalResourceResolver) {
      return delegate;
    }
    return new NullSafeInternalResourceResolver(delegate);
  }

  @Override
  public Resource getResource(String stringUri) throws IOException, URISyntaxException {
    if (stringUri == null || stringUri.trim().isEmpty()) {
      throw new FileNotFoundException("Resource URI is empty");
    }
    return delegate.getResource(stringUri);
  }

  @Override
  public Resource getResource(URI uri) throws IOException {
    if (uri == null) {
      throw new FileNotFoundException("Resource URI is empty");
    }
    return delegate.getResource(uri);
  }

  @Override
  public OutputStream getOutputStream(URI uri) throws IOException {
    return delegate.getOutputStream(uri);
  }

  @Override
  public URI resolveFromBase(URI uri) {
    return delegate.resolveFromBase(uri);
  }

  private static final class DelegatingResourceResolver implements ResourceResolver {
    private final InternalResourceResolver delegate;

    private DelegatingResourceResolver(InternalResourceResolver delegate) {
      this.delegate = delegate;
    }

    @Override
    public Resource getResource(URI uri) throws IOException {
      return delegate.getResource(uri);
    }

    @Override
    public OutputStream getOutputStream(URI uri) throws IOException {
      return delegate.getOutputStream(uri);
    }
  }
}
