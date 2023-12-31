/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.service.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SmallTest
@RunWith(JUnit4.class)
public class WallpaperServiceTest {

    @Test
    public void testDeliversAmbientModeChanged() {
        int[] ambientModeChangedCount = {0};
        WallpaperService service = new WallpaperService() {
            @Override
            public Engine onCreateEngine() {
                return new Engine() {
                    @Override
                    public void onAmbientModeChanged(boolean inAmbientMode, long duration) {
                        ambientModeChangedCount[0]++;
                    }
                };
            }
        };
        WallpaperService.Engine engine = service.onCreateEngine();
        engine.setCreated(true);

        engine.doAmbientModeChanged(false, 0);
        assertFalse("ambient mode should be false", engine.isInAmbientMode());
        assertEquals("onAmbientModeChanged should have been called",
                ambientModeChangedCount[0], 1);

        engine.doAmbientModeChanged(true, 0);
        assertTrue("ambient mode should be false", engine.isInAmbientMode());
        assertEquals("onAmbientModeChanged should have been called",
                ambientModeChangedCount[0], 2);
    }

    @Test
    public void testDeliversZoomChanged() {
        int[] zoomChangedCount = {0};
        WallpaperService service = new WallpaperService() {
            @Override
            public Engine onCreateEngine() {
                return new Engine() {
                    @Override
                    public void onZoomChanged(float zoom) {
                        super.onZoomChanged(zoom);
                        zoomChangedCount[0]++;
                    }
                };
            }
        };
        WallpaperService.Engine engine = service.onCreateEngine();
        engine.setCreated(true);

        engine.setZoom(.5f);
        assertEquals("engine scale was not updated", .5f, engine.getZoom(), .001f);
        assertEquals("onZoomChanged should have been called", 1, zoomChangedCount[0]);

        engine.setZoom(0);
        assertEquals("engine scale was not updated", 0, engine.getZoom(), .001f);
        assertEquals("onAmbientModeChanged should have been called", 2, zoomChangedCount[0]);
    }

    @Test
    public void testNotifyColorsOfDestroyedEngine_doesntCrash() {
        WallpaperService service = new WallpaperService() {
            @Override
            public Engine onCreateEngine() {
                return new Engine();
            }
        };
        WallpaperService.Engine engine = service.onCreateEngine();
        engine.detach();

        engine.notifyColorsChanged();
    }
}
