package org.apache.fop.apps.io;

import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class NullSafeInternalResourceResolverTest {

  @Test
  public void getResource_nullStringUri_throwsFileNotFoundInsteadOfNpe() throws Exception {
    InternalResourceResolver resolver = createResolver(new AtomicReference<>());
    InternalResourceResolver wrapped = NullSafeInternalResourceResolver.wrap(resolver);

    assertThrows(FileNotFoundException.class, () -> wrapped.getResource((String) null));
  }

  @Test
  public void getResource_nullUri_throwsFileNotFoundInsteadOfNpe() {
    InternalResourceResolver resolver = createResolver(new AtomicReference<>());
    InternalResourceResolver wrapped = NullSafeInternalResourceResolver.wrap(resolver);

    assertThrows(FileNotFoundException.class, () -> wrapped.getResource((URI) null));
  }

  @Test
  public void getResource_nonEmptyUri_delegatesToOriginalResolver() throws Exception {
    AtomicReference<URI> capturedUri = new AtomicReference<>();
    InternalResourceResolver resolver = createResolver(capturedUri);
    InternalResourceResolver wrapped = NullSafeInternalResourceResolver.wrap(resolver);

    wrapped.getResource("images/logo.png");

    assertEquals(URI.create("file:/tmp/base/images/logo.png"), capturedUri.get());
  }

  @Test
  public void wrap_alreadyWrappedResolver_returnsSameInstance() {
    InternalResourceResolver resolver = createResolver(new AtomicReference<>());
    InternalResourceResolver wrapped = NullSafeInternalResourceResolver.wrap(resolver);

    assertSame(wrapped, NullSafeInternalResourceResolver.wrap(wrapped));
  }

  private static InternalResourceResolver createResolver(AtomicReference<URI> capturedUri) {
    ResourceResolver delegate = new ResourceResolver() {
      @Override
      public Resource getResource(URI uri) {
        capturedUri.set(uri);
        return new Resource(new ByteArrayInputStream(new byte[0]));
      }

      @Override
      public OutputStream getOutputStream(URI uri) {
        return new ByteArrayOutputStream();
      }
    };
    try {
      return new InternalResourceResolver(new URI("file:/tmp/base/"), delegate);
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }
}
