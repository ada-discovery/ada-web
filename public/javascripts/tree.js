    $.widget( "custom.tree", {
        // default options
        options: {
            jsonData: null,
            showNodeFun: null,
            dragRelocateToParent: null,
            duration: 750,
            width: 960,
            height: 800,
            maxTextLength: 20
        },

        // the constructor
        _create: function() {
            this.i = 0;

            var margin = {top: 0, right: 100, bottom: 0, left: 100};

            var elementId = this.element.attr("id");
            this.svg = d3.select("#" + elementId).append("svg")
                .attr("width", this.options.width)
                .attr("height", this.options.height)
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")")


            var width = this.element.width() - margin.right - margin.left,
                height = this.options.height - margin.top - margin.bottom;

            this.tree = d3.layout.tree()
                .size([height, width]);

            this.diagonal = d3.svg.diagonal()
                .projection(function (d) {
                    return [d.y, d.x];
                });


            var that = this;
            that.root = this.options.jsonData;
            that.root.x0 = height / 2;
            that.root.y0 = 0;

            this.drag = d3.behavior.drag()
                .on('dragstart', function(node) {
                    that._dragstart(node, this)
                })
                .on("drag", function(node) {
                    that._drag(node, this)
                })
                .on("dragend", function(node) {
                    that._dragend(node, this)
                })

            that.collapseAll()
            that._expand(that.root)
            that._update(that.root);

            d3.select(self.frameElement).style("height", this.options.height + "px");

            $(window).resize(function(){
                that._update(that.root);
            });
        },

        expandAll: function() {
            this._expandRecursively(this.root)
            this._update(this.root);
        },

        collapseAll: function() {
            this._collapseRecursively(this.root)
            this._update(this.root);
        },

        addToRoot: function(id, name) {
            var node = {}
            node.name = name;
            node._id = id;
            node.parent = this.root;

            this._expand(this.root);

            if (this.root.children)
                this.root.children.push(node)
            else
                this.root.children = [node];

            node.depth = this.root.depth + 1
            this._update(this.root);
        },

        _dragstart: function (node, uiNode) {
            node.dragstartx = node.x
            node.dragstarty = node.y
            //d3.event.sourceEvent.stopPropagation();
        },

        _drag: function(node, uiNode) {
            if (node == this.root)
                return;
            var that = this

            node.x += d3.event.dy
            node.y += d3.event.dx

            d3.select(uiNode).attr("transform", "translate(" + node.y + "," + node.x + ")");

            var allUINodes = this.svg.selectAll("g.node")
            allUINodes[0].forEach(function(uiNode2) {
                var node2 = uiNode2.__data__
                if (node.id != node2.id) {
                    if (that._areNodesClose(node, node2) && !that._isPredecessor(node, node2)) {
                        that._transitionCircle(uiNode2).style("fill", "red");
                    } else {
                        that._transitionCircle(uiNode2).style("fill", function(d) {return that._isCollapsed(d) ? "lightsteelblue" : "#fff"});
                    }
                }
            });
        },

        _dragend: function(node, uiNode) {
            var that = this

            var matchFound = false;
            var allUINodes = this.svg.selectAll("g.node")
            allUINodes[0].forEach(function(parentUiNode) {
                var parent = parentUiNode.__data__
                if (that._areNodesClose(node, parent) && !that._isPredecessor(node, parent)) {
                    var index = node.parent.children.indexOf(node);
                    node.parent.children.splice(index, 1);
                    node.parent = parent;

                    that._expand(parent)
                    if (parent.children)
                        parent.children.push(node)
                    else
                        parent.children = [node];

                    node.depth = parent.depth + 1
                    that._update(parent);
                    if (that.options.dragRelocateToParent)
                        that.options.dragRelocateToParent(node, parent)
                    matchFound = true
                    return;
                }
            });
            if (!matchFound) {
                // revert back to the original position
                node.x = node.dragstartx
                node.y = node.dragstarty
                d3.select(uiNode).transition().attr("transform", "translate(" + node.y + "," + node.x + ")");
            }
        },

        _update: function(source) {
            var that = this;

            // Compute the new tree layout.
            var nodes = this.tree.nodes(this.root).reverse(),
                links = this.tree.links(nodes);

            var depths = $.map( nodes, function(val){ return val.depth; })
            var maxDepth = Math.max.apply( null, depths);

            var step = 0;
            if (maxDepth > 0)
                step = 0.75 * this.element.width() / maxDepth;

            // Normalize for fixed-depth.
            nodes.forEach(function(d) { d.y = d.depth * step; });

            // Update the nodes…
            var uiNodes = this.svg.selectAll("g.node")
                .data(nodes, function(d) {
                    return d.id || (d.id = ++(that.i));
                });

            // Enter any new nodes at the parent's previous position.
            var nodeEnter = uiNodes.enter().append("g")
                .attr("class", "node")
                .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })

            nodeEnter.call(this.drag)

            nodeEnter.append("circle")
                .attr("r", 1e-6)
                .style("fill", function(d) { return that._isCollapsed(d) ? "lightsteelblue" : "#fff"; })
                .on("click", function(d) {
                    if (d3.event.defaultPrevented) return;
                    if (d.children)
                        that._collapse(d)
                    else
                        that._expand(d)
                    that._update(d);
                })

            nodeEnter.append("text")
                .attr("x", function(d) { return that._isInnerNode(d) ? -10 : 10; })
                .attr("dy", ".35em")
                .attr("text-anchor", function(d) { return that._isInnerNode(d) ? "end" : "start"; })
                .text(function(d) {
                    var maxLength = that.options.maxTextLength;
                    return (d.name.length > maxLength) ? d.name.substring(0, maxLength - 2) + ".." : d.name;
                })
                .style("fill-opacity", 1e-6)
                .on("dblclick", function(d) {
                    that.options.showNodeFun(d)
                })

            // Transition nodes to their new position.
            var nodeUpdate = uiNodes.transition()
                .duration(this.options.duration)
                .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; });

            nodeUpdate.select("circle")
                .attr("r", 7)
                .style("fill", function(d) { return that._isCollapsed(d) ? "lightsteelblue" : "#fff"; });

            nodeUpdate.select("text")
                .style("fill-opacity", 1);

            // Transition exiting nodes to the parent's new position.
            var nodeExit = uiNodes.exit().transition()
                .duration(this.options.duration)
                .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
                .remove();

            nodeExit.select("circle")
                .attr("r", 1e-6);

            nodeExit.select("text")
                .style("fill-opacity", 1e-6);

            // Update the links…
            var link = this.svg.selectAll("path.link")
                .data(links, function(d) { return d.target.id; });

            // Enter any new links at the parent's previous position.
            link.enter().insert("path", "g")
                .attr("class", "link")
                .attr("d", function(d) {
                    var o = {x: source.x0, y: source.y0};
                    return that.diagonal({source: o, target: o});
                });

            // Transition links to their new position.
            link.transition()
                .duration(this.options.duration)
                .attr("d", this.diagonal);

            // Transition exiting nodes to the parent's new position.
            link.exit().transition()
                .duration(this.options.duration)
                .attr("d", function(d) {
                    var o = {x: source.x, y: source.y};
                    return that.diagonal({source: o, target: o});
                })
                .remove();

            // Stash the old positions for transition.
            nodes.forEach(function(d) {
                d.x0 = d.x;
                d.y0 = d.y;
            });
        },

        // Helper functions

        _isInnerNode: function(node) {
            return (node.children && node.children.length > 0) || (node._children && node._children.length > 0);
        },

        _isCollapsed: function (node) {
            return node._children && node._children.length > 0;
        },

        _areNodesClose: function(node, node2) {
            return node.id != node2.id && Math.abs(node.x - node2.x) < 10 && Math.abs(node.y - node2.y) < 10
        },

        _isPredecessor: function(node, node2) {
            if (node2.parent == node)
                return true
            if (node2.parent)
                return this._isPredecessor(node, node2.parent)
            else
                return false;
        },

        _collapse: function(node) {
            if (node.children) {
                node._children = node.children;
                node.children = null;
            }
        },

        _expand: function(node) {
            if (node._children) {
                node.children = node._children;
                node._children = null;
            }
        },

        _collapseRecursively: function(node) {
            var that = this;
            if (node.children)
                $.each(node.children, function(i, child) {
                    that._collapseRecursively(child)
                });
            that._collapse(node);
        },

        _expandRecursively: function(node) {
            var that = this;
            that._expand(node);
            if (node.children)
                $.each(node.children, function(i, child) {
                    that._expandRecursively(child)
                });
        },

        _transitionCircle: function(element) {
            return d3.select(element).select("circle").transition()
        }
    });