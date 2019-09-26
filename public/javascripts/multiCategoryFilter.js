$.widget( "custom.multiCategoryFilter", {
    // default options
    options: {
        jsonConditions: null,
        fieldNameAndLabels: null,
        getFieldsUrl: null,
        submitUrl: null,
        submitAjaxFun: null,
        listFiltersUrl: null,
        saveFilterAjaxFun: null,
        filterSubmitParamName: null,
        filterId: null, // not an element id but a persisted id of the filter if any
        createSubmissionJson: null
    },

    // the constructor
    _create: function() {
        var that = this;

        this.categoryTreeFieldNodeDatas = [];

        var fieldDisplayChoiceCallback = null;
        var initFilterIfNeededTreeCallback = null;

        this.categoryTreeElement = this.element.find(".categoricalTree");

        this.categoryTreeElement.on('open_node.jstree', function (event, data) {
            data.instance.set_type(data.node,'category-open');
        });

        this.categoryTreeElement.on('close_node.jstree', function (event, data) {
            data.instance.set_type(data.node,'category');
        });

        var fieldDisplayChoiceCallback = function(choiceVal) {
            that._updateCategoryTreeForFieldDisplayChoice(choiceVal)
        }

        if (that.options.fieldNameAndLabels) {
            this._initCategoryTree()
        } else {
            initFilterIfNeededTreeCallback = function() {
                that._initCategoryTree()
            }
        }

        this.element.multiFilter({
            jsonConditions: this.options.jsonConditions,
            fieldNameAndLabels: this.options.fieldNameAndLabels,
            getFieldsUrl: this.options.getFieldsUrl,
            submitUrl: this.options.submitUrl,
            submitAjaxFun: this.options.submitAjaxFun,
            listFiltersUrl: this.options.listFiltersUrl,
            saveFilterAjaxFun: this.options.saveFilterAjaxFun,
            filterSubmitParamName: this.options.filterSubmitParamName,
            filterId: this.options.filterId,
            createSubmissionJson: this.options.createSubmissionJson,
            initFilterIfNeededCallback: initFilterIfNeededTreeCallback,
            fieldDisplayChoiceCallback: fieldDisplayChoiceCallback
        })
    },

    _initCategoryTreeFieldNodeDatas: function() {
        var that = this;

        that.categoryTreeFieldNodeDatas = Object.values(that.categoryTreeElement.jstree(true)._model.data).filter(
            function(node) {
                return node.type.startsWith("field-");
            }
        ).map(function (field, index) { return {id: field.id, label: field.data.label, nonNullCount: field.data.nonNullCount}; });
    },

    _updateCategoryTreeForFieldDisplayChoice: function(choiceVal) {
        var that = this;

        if (that.categoryTreeFieldNodeDatas.length == 0) {
            that._initCategoryTreeFieldNodeDatas();
        }

        $.each(that.categoryTreeFieldNodeDatas, function( index, field ) {
            var text;
            var nonNullCount = field.nonNullCount;
            switch (choiceVal) {
                case 0: text = field.id; break;
                case 1: text = field.label; break;
                case 2: text = (field.label) ? field.label : field.id; break;
                case 3: text = (field.label) ? field.label : field.id; break;
            }
            if (text) {
                if (nonNullCount) {
                    text = text + " (" + nonNullCount + ")"
                }
                that.categoryTreeElement.jstree('set_text', field.id, text);
                that.categoryTreeElement.jstree(true).show_node(field.id);
            } else {
                that.categoryTreeElement.jstree(true).hide_node(field.id);
            }
        });
        that.categoryTreeElement.jstree("deselect_all");
    },

    _initCategoryTree: function() {
        var that = this;
        that.categoryTreeElement.jsTreeWidget('init');
        that.categoryTreeElement.jstree("deselect_all");
        that.categoryTreeElement.bind('ready.jstree', function(e, data) {
            var rootNode = that.categoryTreeElement.jstree(true).get_node("#")
            if(rootNode.children.length == 0) {
                $("#showCategoryTreeButton").hide();
            }
        })
    }
})