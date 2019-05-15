$.widget( "custom.dynamicTable", {
    // default options
    options: {
        domainName: null,
        rowToModel: null,
        itemToRow: null,
        sortable: false,
        hideModalOnEnter: true
    },

    // the constructor
    _create: function() {
        var that = this;
        var domainName = this.options.domainName;
        this.tableDiv = this.element;
        this.tableBody = this.tableDiv.find("table tbody")

        this.modalName = 'add_' + domainName + 'Modal'
        this.submitButtonName = domainName + 'SubmitButton'
        this.itemsName = domainName + 's[]'

        handleModalButtonEnterPressed(this.modalName, this.submitButtonName, function() { that.addTableRowFromModal()}, that.options.hideModalOnEnter)

        $('form').submit(function(ev) {
            ev.preventDefault();
            that.updateModelFromTable();
            this.submit();
        });

        // note that sortable (draggable) functionality requires jquery-ui js lib
        if (this.options.sortable) {
            this.tableBody.sortable();
        }
    },

    openAddModal: function() {
        $('#' + this.modalName +' input, #' + this.modalName +' select, #' + this.modalName +' textarea').each(function () {
            if (this.id) {
                $(this).val("");
                if ($(this).attr('type') == "checkbox") {
                    $(this).prop('checked', false);
                }
            }
        })
        $('#' + this.modalName).modal()
    },

    _getAddModalValues: function() {
        return getModalValues(this.modalName);
    },

    addTableRowFromModal: function() {
        this.addTableRow(this._getAddModalValues())
    },

    addTableRows: function(multiValues) {
        var that = this;
        $.each(multiValues, function(index, values) {
            that._addTableRow(values)
        })

        this.element.trigger("rowsAdded", multiValues.length);
    },

    addTableRow: function(values) {
        this._addTableRow(values)

        this.element.trigger("rowAdded");
    },

    _addTableRow: function(values) {
        this.tableBody.append(this.options.itemToRow(values))
    },

    removeRows: function() {
        var that = this;
        var count = 0
        this.tableBody.find('tr').each(function() {
            var checked = $(this).find("td input.table-selection[type=checkbox]").is(':checked');
            if (checked) {
                $(this).remove();
                count++;
            }
        });

        this.element.trigger("rowsRemoved", count);
    },

    replaceTableRow: function(rowIndex, values) {
        var row = this.tableBody.find('tr').eq(rowIndex);
        row.after(this.options.itemToRow(values));
        row.remove();
        this.element.trigger("rowReplaced", rowIndex);
    },

    removeTableModel: function() {
        this.tableDiv.find("input[name='" + this.itemsName + "']").each(function() {
            $(this).remove();
        });
    },

    updateModelFromTable: function() {
        this.removeTableModel();
        this.addToModelFromTable();
    },

    addToModelFromTable: function() {
        var that = this;
        this.tableBody.find('tr').each(function() {
            var item = that.options.rowToModel($(this));
            var map = {};
            map[that.itemsName] = item;
            addParams(that.tableDiv, map)
        });
    },

    getModel: function() {
        var model = this.tableDiv.find("input[name='" + this.itemsName + "']").map(function() {
            return $(this).val().trim();
        }).get();
        return model;
    }
})