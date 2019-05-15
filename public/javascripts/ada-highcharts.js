    function pieChart(
        title,
        chartElementId,
        series,
        showLabels,
        showLegend,
        pointFormat,
        height,
        allowSelectionEvent,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                plotBackgroundColor: null,
                plotBorderWidth: null,
                plotShadow: false,
                type: 'pie',
                height: height
            },
            title: {
                text: title
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            plotOptions: {
                pie: {
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    dataLabels: {
                        enabled: showLabels,
                        format: '<b>{point.name}</b>: {point.percentage:.1f}%',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    },
                    showInLegend: showLegend,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    }
                },
                series: {
                    animation: {
                        duration: 400
                    }
                }
            },
            legend: {
                maxHeight: 70
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            series: series
        });
    }

    function columnChart(
        title,
        chartElementId,
        categories,
        series,
        inverted,
        xAxisCaption,
        yAxisCaption,
        showLabels,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null

        var chartType = 'column'
        if (inverted)
            chartType = 'bar'
        $('#' + chartElementId).highcharts({
            chart: {
                type: chartType,
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: 'x'
            },
            title: {
                text: title
            },
            xAxis: {
//                type: 'category',
                type: dataType,
                title: {
                    text: xAxisCaption
                },
                categories: categories,
                crosshair: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                }
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width:'100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    borderWidth: 0,
                    pointPadding: 0.07,
                    groupPadding: 0.05,
                    dataLabels: {
                        enabled: showLabels,
                        formatter: function() {
                            var value = this.point.y
                            return (value === parseInt(value, 10)) ? value : Highcharts.numberFormat(value, 1)
                        }
                    },
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    function lineChart(
        title,
        chartElementId,
        categories,
        series,
        xAxisCaption,
        yAxisCaption,
        showLegend,
        enableDataLabels,
        pointFormat,
        height,
        dataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange,
        xMin,
        xMax,
        yMin,
        yMax
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null
        var zoomType = (allowIntervalSelectionEvent) ? 'x' : null

        $('#' + chartElementId).highcharts({
            chart: {
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: zoomType
            },
            title: {
                text: title
            },
            xAxis: {
                type: dataType,
                title: {
                    text: xAxisCaption
                },
                min: xMin,
                max: xMax,
                categories: categories
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                },
                min: yMin,
                max: yMax
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width:'80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    marker: {
                        enabled: enableDataLabels,
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true
                            }
                        }
                    },
                    turboThreshold: 5000,
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    function polarChart(
        title,
        chartElementId,
        categories,
        series,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowSelectionEvent,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                polar: true,
                height: height
            },

            title: {
                text: title
            },
            pane: {
                center: ['50%', '52%'],
                size: '90%',
                startAngle: 0,
                endAngle: 360
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width:'80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            xAxis: {
                type: dataType,
                categories: categories,
                tickmarkPlacement: 'on',
                lineWidth: 0
            },

            yAxis: {
                gridLineInterpolation: 'polygon',
                labels: {
                    enabled: true
                }
            },
            plotOptions: {
                series: {
                    dataLabels: {
                        enabled: false
                    },
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            series: series
        });
    }

    function scatterChart(
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        showLegend,
        pointFormat,
        height,
        xDataType,
        yDataType,
        zoomIfDragged,
        allowSelectionEvent
    ) {

        var pointFormatter = null
        if (!pointFormat) {
            pointFormatter = defaultScatterPointFormatter(xDataType, yDataType)
        } else if (typeof pointFormat === "function") {
            pointFormatter = pointFormat
            pointFormat = null
        }

        /**
         * Custom selection handler that selects points and cancels the default zoom behaviour
         */
        function selectPointsByDrag(e) {

            // Select points
            Highcharts.each(this.series, function (series) {
                Highcharts.each(series.points, function (point) {
                    if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                        point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                        point.select(true, true);
                    }
                });
            });

            toast(this, '<b>' + this.getSelectedPoints().length + ' points selected.</b>' +
                '<br>Click on empty space to deselect.');

            if (allowSelectionEvent) triggerSelectionEvent(this.series, e)

            return false; // Don't zoom
        }

        function zoomByDrag(e) {
            if (e.xAxis && e.yAxis) {
                // count the selected points
                var count = 0

                Highcharts.each(this.series, function (series) {
                    Highcharts.each(series.points, function (point) {
                        if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                            point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                            count++;
                        }
                    });
                });

//                toast(this, '<b>' + count + ' points selected.</b>');

                if (allowSelectionEvent) triggerSelectionEvent(this.series, e)
            }

            return true;
        }

        function triggerSelectionEvent(series, event) {
            var cornerPoints = findCornerPoints(series, event)
            if (cornerPoints)
                $('#' + chartElementId).trigger("areaSelected", cornerPoints);
        }

        /**
         * On click, unselect all points
         */
        function unselectByClick() {
            var points = this.getSelectedPoints();
            if (points.length > 0) {
                Highcharts.each(points, function (point) {
                    point.select(false);
                });
            }
        }

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'scatter',
                zoomType: 'xy',
                events: {
                    selection: (zoomIfDragged) ? zoomByDrag : selectPointsByDrag,
                    click: unselectByClick
                },
                height: height
            },
            title: {
                text:  title
            },
            xAxis: {
                title: {
                    enabled: true,
                    text: xAxisCaption
                },
                type: xDataType,
                startOnTick: true,
                endOnTick: true,
                showLastLabel: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                },
                type: yDataType
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width:'100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            plotOptions: {
                scatter: {
                    marker: {
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true,
                                lineColor: 'rgb(100,100,100)'
                            }
                        }
                    },
                    states: {
                        hover: {
                            marker: {
                                enabled: false
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    },
                    turboThreshold: 5000
                }
            },
            tooltip:{
                pointFormat: pointFormat,
                formatter: pointFormatter
           },
            series: series
        });
    }

    function defaultScatterPointFormatter(xDataType, yDataType) {
        var formatter = function () {
            var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x
            var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y

            return '<b>' + this.series.name + '</b><br>' + xPoint + ", " + yPoint
        }
        return formatter;
    }

    function heatmapChart(
        title,
        chartElementId,
        xCategories,
        yCategories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        twoColors,
        height
    ) {
        var colors = (twoColors) ?
            [
                [0, Highcharts.getOptions().colors[5]],
                [0.5, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ] : [
                [0, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ]

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'heatmap',
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                categories: xCategories,
                title: {
                    enabled: (xAxisCaption != null),
                    text: xAxisCaption
                },
                labels: {
                    formatter: function() {
                        return shorten(this.value, 10);
                    }
                }
            },
            yAxis: {
                categories: yCategories,
                title: {
                    enabled: (yAxisCaption != null),
                    text: yAxisCaption
                },
                labels: {
                    formatter: function() {
                        return shorten(this.value, 10);
                    }
                }
            },
            credits: {
                enabled: false
            },
            colorAxis: {
                stops: colors,
                min: min,
                max: max
//                    minColor: '#FFFFFF',
//                    maxColor: Highcharts.getOptions().colors[0]
            },
            legend: {
                align: 'right',
                layout: 'vertical',
                margin: 0,
                verticalAlign: 'top',
                y: 25,
                symbolHeight: 280
            },
            plotOptions: {
                series: {
                    boostThreshold: 100
                }
            },
            boost: {
                useGPUTranslations: true
            },
            tooltip: {
                formatter: function () {
                    var value = (this.point.value != null) ? Highcharts.numberFormat(this.point.value, 3, '.') : "Undefined"
                    return '<b>' + this.series.xAxis.categories[this.point.x] + '</b><br><b>' +
                        this.series.yAxis.categories[this.point.y] + '</b><br>' + value;
                },
                valueDecimals: 2
            },
            series: [{
                name: title,
                borderWidth: 1,
                data: data,
                dataLabels: {
                    enabled: false,
                    color: '#000000'
                }
            }]
        });
    }

    function boxPlot(
        title,
        chartElementId,
        categories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        pointFormat,
        height,
        dataType
    ) {
        $('#' + chartElementId).highcharts({
            chart: {
                type: 'boxplot',
                height: height
            },
            title: {
                text:  title
            },
            xAxis: {
                categories: categories,
                title: {
                    text: xAxisCaption
                }
            },
            yAxis: {
                type: dataType,
                title: {
                    text: yAxisCaption
                },
                min: min,
                max: max
            },
            legend: {
                enabled: false
            },
            credits: {
                enabled: false
            },
            plotOptions: {
                boxplot: {
                    fillColor: '#eeeeee',
                    lineWidth: 2,
                    medianWidth: 3,
                    animation: {
                        duration: 400
                    }
                }
            },
            series: [{
                name: title,
                data: data,
                tooltip: {
                    headerFormat: '<b>{point.series.name}</b><br/>',
                    pointFormat:  pointFormat
                }
            }]
        });
    }

    function chartTypeMenu(chartElementId, chartTypes) {
        Highcharts.setOptions({
            lang: {
                chartTypeButtonTitle: "Chart Type"
            }
        });

        return {
            buttons: {
                chartTypeButton: {
                    symbol: 'circle',
                    x: '0%',
                    _titleKey: "chartTypeButtonTitle",
                    menuItems:
                        chartTypes.map(function(name) {
                            return {
                                text: name,
                                onclick: function () {
                                    $('#' + chartElementId).trigger("chartTypeChanged", name);
                                }
                            }
                        })
                }
            }
        };
    }

    function plotCategoricalChart(chartType, categories, datas, seriesSize, title, yAxisCaption, elementId, showLabels, showLegend, height, pointFormat) {
        var showLegendImpl = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map( function(data, index) {
                    var size = (100 / seriesSize) * (index + 1)
                    var innerSize = 0
                    if (index > 0)
                        innerSize = (100 / seriesSize) * index + 1
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                pieChart(title, elementId, series, showLabels, showLegend, pointFormat, height, true, true);
                break;
            case 'Column':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                columnChart(title, elementId, categories, series, false, '', yAxisCaption, true, showLegendImpl, pointFormat, height, null, true, false, true);
                break;
            case 'Bar':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                columnChart(title, elementId, categories, series, true, '', yAxisCaption, true, showLegendImpl, pointFormat, height, null, true, false, true);
                break;
            case 'Line':
                var series = datas

                lineChart(title, elementId, categories, series, '', yAxisCaption, showLegendImpl, true, pointFormat, height, null, true, false, true);
                break;
            case 'Spline':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                lineChart(title, elementId, categories, series, '', yAxisCaption, showLegendImpl, true, pointFormat, height, null, true, false,true);
                break;
            case 'Polar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                polarChart(title, elementId, categories, series, showLegendImpl,  pointFormat, height, null, true, true);
                break;
        }
    }

    function plotNumericalChart(chartType, datas, seriesSize, title, xAxisCaption, yAxisCaption, elementId, height, pointFormat, dataType) {
        var showLegend = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map( function(data, index) {
                    var size = (100 / seriesSize) * index
                    var innerSize = Math.max(0, (100 / seriesSize) * (index - 1) + 1)
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                pieChart(title, elementId, series, false, showLegend, pointFormat, height, false, true);
                break;
            case 'Column':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                columnChart(title, elementId, null, series, false, xAxisCaption, yAxisCaption, false, showLegend, pointFormat, height, dataType, false, true,true);
                break;
            case 'Bar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                columnChart(title, elementId, null, series, true, xAxisCaption, yAxisCaption, false, showLegend, pointFormat, height, dataType, false, true,true);
                break;
            case 'Line':
                var series = datas

                lineChart(title, elementId, null, series, xAxisCaption, yAxisCaption, showLegend, true, pointFormat, height, dataType, false, true,true);
                break;
            case 'Spline':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                lineChart(title, elementId, null, series, xAxisCaption, yAxisCaption, showLegend, true, pointFormat, height, dataType, false, true,true);
                break;
            case 'Polar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                polarChart(title, elementId, null, series, showLegend, pointFormat, height, dataType, false, true);
                break;
        }
    }

    function getPointFormatHeader(that) {
        var seriesCount = that.series.chart.series.length
        return (seriesCount > 1) ? '<span style="font-size:11px">' + that.series.name + '</span><br>' : ''
    }

    function getPointFormatPercent(that, totalCounts) {
        return ' (<b>' + Highcharts.numberFormat(100 * that.y / totalCounts[that.series.index], 1) + '%</b>)'
    }

    function getPointFormatPercent2(that) {
        return ': <b>' + Highcharts.numberFormat(that.y, 1) + '%</b>'
    }

    function getPointFormatY(that, yFloatingPoints) {
        var yValue = (yFloatingPoints) ? Highcharts.numberFormat(that.y, yFloatingPoints) : that.y
        return ': <b>' + yValue + '</b>'
    }

    function getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) {
        var colorStartPart = '<span style="color:' + that.point.color + '">'
        var valuePart =
            (isDate) ?
                Highcharts.dateFormat('%Y-%m-%d', that.point.x)
                :
                (isDouble) ?
                    ((xFloatingPoints) ? Highcharts.numberFormat(that.point.x, xFloatingPoints) : that.point.x)
                 :
                    that.point.x;

        return colorStartPart + valuePart + '</span>'
    }

    function getPointFormatCategoricalValue(that) {
        return '<span style="color:' + that.point.color + '">' + that.point.name + '</span>'
    }

    function numericalPointFormat(isDate, isDouble, that, xFloatingPoints, yFloatingPoints) {
        return getPointFormatHeader(that) +
            getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) +
            getPointFormatY(that, yFloatingPoints)
    }

    function numericalCountPointFormat(isDate, isDouble, totalCounts, that) {
        return getPointFormatHeader(that) +
            getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            getPointFormatY(that) +
            getPointFormatPercent(that, totalCounts);
    }

    function categoricalCountPointFormat(totalCounts, that) {
        return getPointFormatHeader(that) +
            getPointFormatCategoricalValue(that) +
            getPointFormatY(that) +
            getPointFormatPercent(that, totalCounts)
    }

    function numericalPercentPointFormat(isDate, isDouble, that) {
        return getPointFormatHeader(that) +
            getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            getPointFormatPercent2(that);
    }

    function categoricalPercentPointFormat(that) {
        return getPointFormatHeader(that) +
            getPointFormatCategoricalValue(that) +
            getPointFormatPercent2(that)
    }

    /**
     * Display a temporary label on the chart
     */
    function toast(chart, text) {
        if (chart.toast)
            chart.toast = chart.toast.destroy();

        chart.toast = chart.renderer.label(text, 100, 120)
            .attr({
                fill: Highcharts.getOptions().colors[0],
                padding: 10,
                r: 5,
                zIndex: 8
            })
            .css({
                color: '#FFFFFF'
            })
            .add();

        setTimeout(function () {
            if (chart.toast)
                chart.toast.fadeOut();
        }, 2000);

        setTimeout(function () {
            if (chart.toast) {
                chart.toast = chart.toast.destroy();
            }
        }, 2500);
    }

    function refreshHighcharts() {
        Highcharts.charts.forEach(function (chart) {
            if (chart) chart.reflow();
        });
    }

    function findCornerPoints(series, event) {
        var xMin, xMax, yMin, yMax;
        Highcharts.each(series, function (series) {
            Highcharts.each(series.points, function (point) {
                if (point.x >= event.xAxis[0].min && point.x <= event.xAxis[0].max &&
                    point.y >= event.yAxis[0].min && point.y <= event.yAxis[0].max) {

                    if (!xMin || point.x < xMin)
                        xMin = point.x

                    if (!xMax || point.x > xMax)
                        xMax = point.x

                    if (!yMin || point.y < yMin)
                        yMin = point.y

                    if (!yMax || point.y > yMax)
                        yMax = point.y
                }
            });
        });
        return (xMin) ? {xMin: xMin, xMax: xMax, yMin: yMin, yMax: yMax} : null
    }