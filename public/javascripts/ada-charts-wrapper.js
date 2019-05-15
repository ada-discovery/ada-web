$.widget( "custom.ada_charts", {
    // default options
    options: {
        chartType: null
    },

    // the constructor
    _create: function() {
        var that = this;

        console.log(this.options.chartType)

        this.container = document.querySelector('#' + this.element.attr("id"));
        this.chart = AdaCharts.chart({chartType: this.options.chartType, container: this.container});
        this._addExportButton();
    },

    update: function(data) {
      this.data = data;
      this.refresh();
    },

    refresh: function() {
        if (this.data) {
            this.chart.update(this.data);
            this.exportMenu.show();
        }
    },

    _addExportButton: function() {
        var that = this;

        var exportButton =
            '<div id="exportMenu" class="dropdown" style="display:none; position: absolute; right: 15px; top: -15px; z-index: 10">\
                <button class="btn btn-default btn-sm dropdown-toggle" type="button" id="dropdownMenu2" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">\
                    <span class="glyphicon glyphicon-align-justify" aria-hidden="true"></span>\
                </button>\
                <ul class="dropdown-menu">\
                    <li><a id="pngExportButton" href="#">PNG</a></li>\
                    <li><a id="svgExportButton" href="#">SVG</a></li>\
            </ul>\
            </div>';

        this.element.prepend(exportButton)
        this.exportMenu = this.element.find("#exportMenu")
        this.pngExportButton = this.element.find("#pngExportButton")
        this.svgExportButton = this.element.find("#svgExportButton")

        this.pngExportButton.on('click', function () {
            that.exportToPNG();
        });

        this.svgExportButton.on('click', function () {
            that.exportToSVG();
        });
    },

    exportToPNG: function() {
        this.chart.toPNG();
    },

    exportToSVG: function() {
        this.chart.toSVG();
    }
})
