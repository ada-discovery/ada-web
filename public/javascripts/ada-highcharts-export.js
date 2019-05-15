$(function () {

    /**
     * Create a global getSVG method that takes an array of charts as an argument. The SVG is returned as an argument in the callback.
     */
    Highcharts.getSVG = function (charts, options, callback) {
        var svgArr = [],
            top = 0,
            width = 0,
            addSVG = function (svgres) {
                // Grab width/height from exported chart
                var svgWidth = +svgres.match(
                    /^<svg[^>]*width\s*=\s*\"?(\d+)\"?[^>]*>/
                    )[1],
                    svgHeight = +svgres.match(
                        /^<svg[^>]*height\s*=\s*\"?(\d+)\"?[^>]*>/
                    )[1],
                    // Offset the position of this chart in the final SVG
                    svg = svgres.replace('<svg', '<g transform="translate(0,' + top + ')" ');
                svg = svg.replace('</svg>', '</g>');
                top += svgHeight;
                width = Math.max(width, svgWidth);
                svgArr.push(svg);
            },
            exportChart = function (i) {
                if (i === charts.length) {
                    return callback('<svg height="' + top + '" width="' + width +
                        '" version="1.1" xmlns="http://www.w3.org/2000/svg">' + svgArr.join('') + '</svg>');
                }
                var chart = $("#" + charts[i]).highcharts()
                if (chart) {
                    chart.getSVGForLocalExport(options, {}, function () {
                        console.log("Failed to get SVG");
                    }, function (svg) {
                        addSVG(svg);
                        return exportChart(i + 1); // Export next only when this SVG is received
                    });
                } else {
                    return exportChart(i + 1);
                }
            };
        exportChart(0);
    };

    /**
     * Create a global exportCharts method that takes an array of charts as an argument,
     * and exporting options as the second argument
     */
    Highcharts.exportCharts = function (charts, options) {
        options = Highcharts.merge(Highcharts.getOptions().exporting, options);

        // Get SVG asynchronously and then download the resulting SVG
        Highcharts.getSVG(charts, options, function (svg) {
            var imageType = options.type;
            Highcharts.downloadSVGLocal(
                svg,
                options,
                // (options.filename || 'chart')  + '.' + (imageType === 'image/svg+xml' ? 'svg' : imageType.split('/')[1]),
                // imageType,
                // options.scale || 2,
                function () {
                    console.log("Failed to export on client side");
                }
            );
        });
    };

    // Set global default options for all charts
    Highcharts.setOptions({
        exporting: {
            fallbackToExportServer: false // Ensure the export happens on the client side or not at all
        }
    });
});