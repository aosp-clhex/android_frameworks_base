/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.server.wm.flicker.testapp;

import static com.android.server.wm.flicker.testapp.ActivityOptions.PortraitOnlyActivity.EXTRA_FIXED_ORIENTATION;

import android.os.Bundle;

public class FixedActivity extends SimpleActivity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the fixed orientation if requested
        if (getIntent().hasExtra(EXTRA_FIXED_ORIENTATION)) {
            final int ori = Integer.parseInt(getIntent().getStringExtra(EXTRA_FIXED_ORIENTATION));
            setRequestedOrientation(ori);
        }
    }
}
