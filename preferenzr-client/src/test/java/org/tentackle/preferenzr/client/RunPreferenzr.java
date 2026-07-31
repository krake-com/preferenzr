/*
 * Test runner.
 */

package org.tentackle.preferenzr.client;

import org.testng.annotations.Test;

/**
 * Starts Preferenzr from within the IDE or via {@code mvn test -Dtest=RunPreferenzr}.
 * <p>
 * The class name deliberately does not match surefire's default includes, so a normal build does not
 * launch the GUI. The database to browse is configured in {@code src/test/filtered-resources/backend.properties}.
 */
public class RunPreferenzr {

  @Test
  public void runPreferenzr() {
    run(null);
  }

  @Test
  public void runPreferenzrDE() {
    run(new String[] {"--locale=de_DE"});
  }


  private synchronized void run(String[] args) {
    PreferenzrFxClient client = new PreferenzrFxClient() {

      @Override
      protected boolean isSystemExitNecessaryToStop() {
        return false;   // no System.exit() by default within the test container
      }

      @Override
      public void stop(int exitValue, Throwable exitThrowable) {
        super.stop(exitValue, exitThrowable);
        if (exitValue != 0) {
          System.exit(exitValue);   // system exit only if error
        }
      }
    };
    client.start(args);
  }

}
