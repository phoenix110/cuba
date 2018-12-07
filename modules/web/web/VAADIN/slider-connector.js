/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

com_company_demo_web_toolkit_ui_slider_SliderServerComponent = function () {
    var connector = this;
    var element = connector.getElement();
    $(element).html("<div/>");
    $(element).css("padding", "5px 10px");

    var slider = $("div", element).slider({
        range: true,
        slide: function (event, ui) {
            connector.valueChanged(ui.values);
        }
    });

    connector.onStateChange = function () {
        var state = connector.getState();

        var data = state.data;

        slider.slider("values", data.values);
        slider.slider("option", "min", data.minValue);
        slider.slider("option", "max", data.maxValue);
        $(element).width(state.width);
    }
};