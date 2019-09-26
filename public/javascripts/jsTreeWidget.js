$.widget( "custom.jsTreeWidget", {
    // default options
    options: {
        data: null,
        typesSetting: null,
        nodeSelectedFun: null,
        nodeDoubleClickedFun: null,
        sortFun: null
    },

    // the constructor
    _create: function() {
        // no-op
    },

    init: function() {
        var that = this
        var sortFun = (that.options.sortFun) ? that.options.sortFun : function(a, b) { return (a.text > b.text) ? 1 : -1 }

        var settings = {
            "core" : {
                "animation" : 0,
                "check_callback" : true,
                "themes" : {
                    'responsive' : false,
                    'variant' : 'large',
                    "stripes" : true
                },
                'data' : that.options.data
            },
            "types" : that.options.typesSetting,
            "search": {
                "case_insensitive": true,
                "show_only_matches" : true,
                "search_leaves_only": true
            },
            "plugins" : [
                "search", "sort", "types", "wholerow" // , "dnd", "contextmenu", "dnd", "state",
            ]
        }

        if (that.options.sortFun)
            settings.sort = that.options.sortFun

        this.element.jstree(settings);
        this.element.jstree("deselect_all");

        if (that.options.nodeSelectedFun) {
            that.element.on("select_node.jstree", function(evt, data) {
                that.options.nodeSelectedFun(data.node);
            });
        }

        if (that.options.nodeDoubleClickedFun) {
            that.element.bind("dblclick.jstree", function (evt) {
                var tree = $(this).jstree();
                var node = tree.get_node(evt.target);
                that.options.nodeDoubleClickedFun(node);
            });
        }
    }
})