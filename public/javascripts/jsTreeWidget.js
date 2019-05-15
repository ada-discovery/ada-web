$.widget( "custom.jsTreeWidget", {
    // default options
    options: {
        data: null,
        typesSetting: null,
        nodeSelectedFun: null
    },

    // the constructor
    _create: function() {
        // no-op
    },

    init: function() {
        var that = this
        this.element.jstree({
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
                "search", "sort", "state", "types", "wholerow" // "contextmenu", "dnd",
            ]
        });

        this.element.jstree("deselect_all");

        if (that.options.nodeSelectedFun) {
            that.element.on("select_node.jstree",
                function(evt, data) {
                    that.options.nodeSelectedFun(data.node);
                }
            );
        }
    }
})