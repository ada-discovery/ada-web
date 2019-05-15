function elementId(widget) {
    return widget._id.$oid + "Widget"
}

function shorten(string, length) {
    var initLength = length || 25
    return (string.length > initLength) ? string.substring(0, initLength) + ".." : string
}

function agg(series, widget) {
    var counts = series.map(function(item) {
        return item.count;
    });

    if (widget.isCumulative) {
        var max = counts.reduce(function(a, b) {
            return Math.max(a, b);
        });

        return max
    } else {
        var sum = counts.reduce(function(a, b) {
            return a + b;
        });

        return sum
    }
}

function categoricalCountWidget(elementId, widget, filterElement) {
    var categories = (widget.data.length > 0) ?
        widget.data[0][1].map(function(count) {
            return count.value
        })
        : []

    var datas = widget.data.map(function(nameSeries){
        var name = nameSeries[0]
        var series = nameSeries[1]

        var sum = agg(series, widget)
        var data = series.map(function(item) {
            var label = shorten(item.value)
            var count = item.count
            var key = item.key

            var value = (widget.useRelativeValues) ? 100 * count / sum : count
            return {name: label, y: value, key: key}
        })

        return {name: name, data: data}
    })

    var totalCounts = widget.data.map(function(nameSeries) {
        return agg(nameSeries[1], widget);
    })

    var seriesSize = datas.length
    var height = widget.displayOptions.height || 400
    var pointFormat = function () {
        return (widget.useRelativeValues) ? categoricalPercentPointFormat(this) : categoricalCountPointFormat(totalCounts, this);
    }
    var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

    $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        plotCategoricalChart(chartType, categories, datas, seriesSize, widget.title, yAxisCaption, elementId, widget.showLabels, widget.showLegend, height, pointFormat);
    });
    plotCategoricalChart(widget.displayOptions.chartType, categories, datas, seriesSize, widget.title, yAxisCaption, elementId, widget.showLabels, widget.showLegend, height, pointFormat)

    if (filterElement) {
        $('#' + elementId).on('pointSelected', function (event, data) {
            var condition = {fieldName: widget.fieldName, conditionType: "=", value: data.key};
            $(filterElement).multiFilter('addConditionsAndSubmit', [condition]);
        });
    }
}

function numericalCountWidget(elementId, widget, filterElement) {
    var isDate = widget.fieldType == "Date"
    var isDouble = widget.fieldType == "Double"
    var dataType = (isDate) ? 'datetime' : null;

    var datas = widget.data.map(function(nameSeries){
        var name = nameSeries[0]
        var series = nameSeries[1]

        var sum = agg(series, widget)
        var data = series.map(function(item) {
            var count = item.count

            var y = (widget.useRelativeValues) ? 100 * count / sum : count
            return [item.value, y]
        })

        return {name: name, data: data}
    })

    var totalCounts = widget.data.map(function(nameSeries) {
        return agg(nameSeries[1], widget);
    })

    var seriesSize = datas.length
    var height = widget.displayOptions.height || 400
    var pointFormat = function () {
        return (widget.useRelativeValues) ? numericalPercentPointFormat(isDate, isDouble, this) : numericalCountPointFormat(isDate, isDouble, totalCounts, this);
    }
    var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

    $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        plotNumericalChart(chartType, datas, seriesSize, widget.title, widget.fieldLabel, yAxisCaption, elementId, height, pointFormat, dataType)
    });

    plotNumericalChart(widget.displayOptions.chartType, datas, seriesSize, widget.title, widget.fieldLabel, yAxisCaption, elementId, height, pointFormat, dataType)

    if (filterElement) {
        function toTypedStringValue(value, ceiling) {
            var intValue = (ceiling) ? Math.ceil(value) : Math.floor(value)

            return (isDate) ? msToStandardDateString(intValue) : (isDouble) ? value.toString() : intValue.toString()
        }

        $('#' + elementId).on('intervalSelected', function (event, data) {
            var xMin = toTypedStringValue(data.xMin, true);
            var xMax = toTypedStringValue(data.xMax, false);

            var conditions = [
                {fieldName: widget.fieldName, conditionType: ">=", value: xMin},
                {fieldName: widget.fieldName, conditionType: "<=", value: xMax}
            ]

            $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
        });
    }
}

function lineWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"
    var isDouble = widget.fieldType == "Double"
    var dataType = (isDate) ? 'datetime' : null;

    var datas = widget.data.map(function(nameSeries){
        return {name: nameSeries[0], data: nameSeries[1]}
    })

    var seriesSize = datas.length
    var showLegend = seriesSize > 1

    var height = widget.displayOptions.height || 400
    var pointFormat = function () {
        return numericalPointFormat(isDate, isDouble, this, 3, 3);
    }

    // $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
    //     plotNumericalChart(chartType, datas, seriesSize, widget.title, widget.xAxisCaption, yAxisCaption, elementId, height, pointFormat, dataType)
    // });

    lineChart(widget.title, elementId, null, datas, widget.xAxisCaption, widget.yAxisCaption, showLegend, true, pointFormat, height, dataType, false, false, false, widget.xMin, widget.xMax, widget.yMin, widget.yMax);
}

function boxWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"
    var dataType = (isDate) ? 'datetime' : null;

    var datas = widget.data.map(function(namedQuartiles) {
        var quartiles = namedQuartiles[1]
        return [quartiles.lowerWhisker, quartiles.lowerQuantile, quartiles.median, quartiles.upperQuantile, quartiles.upperWhisker]
    })

    var categories = widget.data.map(function(namedQuartiles) {
        return namedQuartiles[0]
    })

    var min = widget.min
    var max = widget.max

    var pointFormat = (isDate) ?
        '- Upper 1.5 IQR: {point.high:%Y-%m-%d}<br/>' +
        '- Q3: {point.q3:%Y-%m-%d}<br/>' +
        '- Median: {point.median:%Y-%m-%d}<br/>' +
        '- Q1: {point.q1:%Y-%m-%d}<br/>' +
        '- Lower 1.5 IQR: {point.low:%Y-%m-%d}<br/>'
        :
        '- Upper 1.5 IQR: {point.high}<br/>' +
        '- Q3: {point.q3}<br/>' +
        '- Median: {point.median}<br/>' +
        '- Q1: {point.q1}<br/>' +
        '- Lower 1.5 IQR: {point.low}<br/>'

    var height = widget.displayOptions.height || 400
    boxPlot(widget.title, elementId, categories, widget.xAxisCaption, widget.yAxisCaption, datas, min, max, pointFormat, height, dataType)
}

function scatterWidget(elementId, widget, filterElement) {
    var datas = widget.data.map(function(series) {
        return {name : shorten(series[0]), data : series[1]}
    })

    var height = widget.displayOptions.height || 400;

    var isXDate = widget.xFieldType == "Date"
    var xDataType = (isXDate) ? 'datetime' : null;

    var isYDate = widget.yFieldType == "Date"
    var yDataType = (isYDate) ? 'datetime' : null;

    if (filterElement) {
        addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
    }

    scatterChart(widget.title, elementId, widget.xAxisCaption, widget.yAxisCaption, datas, true, null, height, xDataType, yDataType, true, filterElement != null)
}

function addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate) {
    $('#' + elementId).on('areaSelected', function (event, data) {
        var xMin = (isXDate) ? msToStandardDateString(data.xMin) : data.xMin.toString();
        var xMax = (isXDate) ? msToStandardDateString(data.xMax) : data.xMax.toString();
        var yMin = (isYDate) ? msToStandardDateString(data.yMin) : data.yMin.toString();
        var yMax = (isYDate) ? msToStandardDateString(data.yMax) : data.yMax.toString();

        var conditions = [
            {fieldName: widget.xFieldName, conditionType: ">=", value: xMin},
            {fieldName: widget.xFieldName, conditionType: "<=", value: xMax},
            {fieldName: widget.yFieldName, conditionType: ">=", value: yMin},
            {fieldName: widget.yFieldName, conditionType: "<=", value: yMax}
        ]

        $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
    });
}

function valueScatterWidget(elementId, widget, filterElement) {
    var zs = widget.data.map(function(point) {
        return point[2];
    })

    var zMin = Math.min.apply(null, zs);
    var zMax = Math.max.apply(null, zs);

    var data = widget.data.map(function(point) {
        var zColor = (1 - Math.abs((point[2] - zMin) / zMax)) * 210;
        return {x: point[0], y: point[1], z: point[2], color: 'rgba(255, ' + zColor + ',' + zColor + ', 0.8)'};
    })

    var datas = [{data : data}];

    var height = widget.displayOptions.height || 400;

    var isXDate = widget.xFieldType == "Date";
    var xDataType = (isXDate) ? 'datetime' : null;

    var isYDate = widget.yFieldType == "Date";
    var yDataType = (isYDate) ? 'datetime' : null;

    if (filterElement) {
        addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
    }

    var pointFormatter = function () {
        var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x;
        var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y;
        var zPoint = this.point.z;

        return xPoint + ", " + yPoint + " (" + zPoint + ")";
    }

    scatterChart(widget.title, elementId, widget.xAxisCaption, widget.yAxisCaption, datas, false, pointFormatter, height, xDataType, yDataType, true, filterElement != null)
}

function heatmapWidget(elementId, widget) {
    var xCategories =  widget.xCategories
    var yCategories =  widget.yCategories
    var data = widget.data.map(function(seq, i) {
        return seq.map(function(value, j) {
            return [i, j, value]
        })
    })

    var height = widget.displayOptions.height || 400
    heatmapChart(widget.title, elementId, xCategories, yCategories, widget.xAxisCaption, widget.yAxisCaption, [].concat.apply([], data), widget.min, widget.max, widget.twoColors, height)
};

function htmlWidget(elementId, widget) {
    $('#' + elementId).html(widget.content)
}

function genericWidget(widget, filterElement) {
    var widgetId = elementId(widget)
    genericWidgetForElement(widgetId, widget, filterElement)
}

function genericWidgetForElement(widgetId, widget, filterElement) {
    if(widget.displayOptions.isTextualForm)
        switch (widget.concreteClass) {
            case "org.ada.web.models.CategoricalCountWidget": categoricalTableWidget(widgetId, widget); break;
            case "org.ada.web.models.NumericalCountWidget": numericalTableWidget(widgetId, widget); break;
            case "org.ada.web.models.BasicStatsWidget": basicStatsWidget(widgetId, widget); break;
            default: console.log(widget.concreteClass + " does not have a textual representation.")
        }
    else
        switch (widget.concreteClass) {
            case "org.ada.web.models.CategoricalCountWidget": categoricalCountWidget(widgetId, widget, filterElement); break;
            case "org.ada.web.models.NumericalCountWidget": numericalCountWidget(widgetId, widget, filterElement); break;
            case "org.ada.web.models.BoxWidget": boxWidget(widgetId, widget); break;
            case "org.ada.web.models.ScatterWidget": scatterWidget(widgetId, widget, filterElement); break;
            case "org.ada.web.models.ValueScatterWidget": valueScatterWidget(widgetId, widget, filterElement); break;
            case "org.ada.web.models.HeatmapWidget": heatmapWidget(widgetId, widget); break;
            case "org.ada.web.models.HtmlWidget": htmlWidget(widgetId, widget); break;
            case 'org.ada.web.models.LineWidget': lineWidget(widgetId, widget); break;
            case "org.ada.web.models.BasicStatsWidget": basicStatsWidget(widgetId, widget); break;
            case "org.ada.web.models.IndependenceTestWidget": independenceTestWidget(widgetId, widget); break;
            default: console.log("Widget type" + widget.concreteClass + " unrecognized.")
        }
}

function categoricalTableWidget(elementId, widget) {
    var allCategories = widget.data.map(function(series){ return series[1].map(function(count){ return count.value })});
    var categories = removeDuplicates([].concat.apply([], allCategories))

    var groups = widget.data.map(function(series){ return shorten(series[0], 15) });
    var fieldLabel = shorten(widget.fieldLabel, 15)

    var dataMap = widget.data.map(function(series){
        var map = {}
        $.each(series[1], function(index, count){
            map[count.value] = count.count
        })
        return map
    });

    var rowData = categories.map(function(categoryName) {
        var sum = 0;
        var data = dataMap.map(function(map) {
            var count = map[categoryName] || 0
            sum += count
            return count
        })
        var result = [categoryName].concat(data)
        if (groups.length > 1) {
            result.push(sum)
        }
        return result
    })

    if (categories.length > 1) {
        var counts = widget.data.map(function(series){
            var sum = 0
            $.each(series[1], function(index, count){
                sum += count.count
            })
            return sum
        });

        var totalCount = counts.reduce(function(a,b) {return a + b })

        var countRow = ["<b>Total</b>"].concat(counts)
        if (groups.length > 1) {
            countRow.push(totalCount)
        }
        rowData = rowData.concat([countRow])
    }

    var columnNames = [fieldLabel].concat(groups)
    if (groups.length > 1) {
        columnNames.push("Total")
    }

    var caption = "<h4 align='center'>" + widget.title + "</h4>"

    var height = widget.displayOptions.height || 400
    var div = $("<div style='position: relative; overflow: auto; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

    var table = createTable(columnNames, rowData)

    div.append(caption)
    div.append(table)

    $('#' + elementId).html(div)
}

function numericalTableWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"

    var groups = widget.data.map(function(series){ return shorten(series[0], 15) });
    var fieldLabel = shorten(widget.fieldLabel, 15)
    var valueLength = widget.data[0][1].length

    var rowData = Array.from(Array(valueLength).keys()).map(function(index){
        var row = widget.data.map(function(series){
            var item = series[1]
            var value = item[index].value
            if (isDate) {
                value = new Date(value).toISOString()
            }
            return [value, item[index].count]
        })
        return [].concat.apply([], row)
    })

    var columnNames = groups.map(function(group){return [fieldLabel, group]})
    var columnNames = [].concat.apply([], columnNames)

    var caption = "<h4 align='center'>" + widget.title + "</h4>"

    var height = widget.displayOptions.height || 400
    var div = $("<div style='position: relative; overflow: auto; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

    var table = createTable(columnNames, rowData)

    div.append(caption)

    var centerWrapper = $("<table align='center'>")
    var tr = $("<tr class='vertical-divider' valign='top'>")
    var td = $("<td>")

    td.append(table)
    tr.append(td)
    centerWrapper.append(tr)
    div.append(centerWrapper)

    $('#' + elementId).html(div)
}

function basicStatsWidget(elementId, widget) {
    var caption = "<h4 align='center'>" + widget.title + "</h4>"
    var columnNames = ["Stats", "Value"]

    function roundOrInt(value) {
        return Number.isInteger(value) ? value : value.toFixed(3)
    }

    var data = [
        ["Min", roundOrInt(widget.data.min)],
        ["Max", roundOrInt(widget.data.max)],
        ["Sum", roundOrInt(widget.data.sum)],
        ["Mean", roundOrInt(widget.data.mean)],
        ["Variance", roundOrInt(widget.data.variance)],
        ["STD", roundOrInt(widget.data.standardDeviation)],
        ["# Defined", widget.data.definedCount],
        ["# Undefined", widget.data.undefinedCount]
    ]

    var caption = "<h4 align='center'>" + widget.title + "</h4>"

    var height = widget.displayOptions.height || 400
    var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

    var table = createTable(columnNames, data)

    div.append(caption)

    var centerWrapper = $("<table align='center'>")
    var tr = $("<tr class='vertical-divider' valign='top'>")
    var td = $("<td>")

    td.append(table)
    tr.append(td)
    centerWrapper.append(tr)
    div.append(centerWrapper)

    $('#' + elementId).html(div)
}

function independenceTestWidget(elementId, widget) {
    var caption = "<h4 align='center'>" + widget.title + "</h4>"

    var height = widget.displayOptions.height || 400
    var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

    var table = createIndependenceTestTable(widget.data)

    div.append(caption)

    var centerWrapper = $("<table align='center'>")
    var tr = $("<tr class='vertical-divider' valign='top'>")
    var td = $("<td>")

    td.append(table)
    tr.append(td)
    centerWrapper.append(tr)
    div.append(centerWrapper)

    $('#' + elementId).html(div)
}

function widgetDiv(widget, defaultGridWidth, enforceWidth) {
    var elementIdVal = elementId(widget)

    if (enforceWidth)
        return widgetDivAux(elementIdVal, defaultGridWidth);
    else {
        var gridWidth = widget.displayOptions.gridWidth || defaultGridWidth;
        var gridOffset = widget.displayOptions.gridOffset;

        return widgetDivAux(elementIdVal, gridWidth, gridOffset);
    }
}

function widgetDivAux(elementIdVal, gridWidth, gridOffset) {
    var gridWidthElement = "col-md-" + gridWidth
    var gridOffsetElement = gridOffset ? "col-md-offset-" + gridOffset : ""

    var innerDiv = '<div id="' + elementIdVal + '" class="chart-holder"></div>'
    var div = $("<div class='" + gridWidthElement + " " + gridOffsetElement + "'>")
    div.append(innerDiv)
    return div
}