// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.design.ImageStore;
import io.aerohelper.retroburn.design.Java2dPainter;
import io.aerohelper.retroburn.design.Painter;
import io.aerohelper.retroburn.design.Paper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.ImageView;
import javafx.stage.Window;

/**
 * Prints a sheet at 100 %: the sheet is drawn at 300 dpi and placed in paper coordinates, shifted by
 * the printer's margins so it lands where the PDF would. See {@code docs/design/LLD-cover-designer.md}
 * section 8.
 */
final class CoverPrinting {

    private static final double DPI = 300;
    private static final double POINTS_PER_MM = 72 / 25.4;

    interface Drawing {
        void draw(Painter painter) throws IOException;
    }

    private final Messages messages;
    private final Window owner;

    CoverPrinting(Messages messages, Window owner) {
        this.messages = messages;
        this.owner = owner;
    }

    /**
     * Shows the "print at 100 %" reminder and the print dialog, then prints.
     *
     * @return false if the user cancelled or no printer is available (the user has been told)
     */
    boolean print(Paper paper, boolean landscape, double widthMm, double heightMm, ImageStore images, Drawing drawing)
            throws IOException {
        Alert reminder = new Alert(
                Alert.AlertType.INFORMATION, messages.get("design.print.reminder"), ButtonType.OK, ButtonType.CANCEL);
        reminder.initOwner(owner);
        reminder.setHeaderText(messages.get("design.print.reminderTitle"));
        if (reminder.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return false;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Alert none = new Alert(Alert.AlertType.WARNING, messages.get("design.print.noPrinter"));
            none.initOwner(owner);
            none.setHeaderText(null);
            none.showAndWait();
            return false;
        }
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            return false;
        }
        Printer printer = job.getPrinter();
        PageLayout layout = printer.createPageLayout(
                paper == Paper.LETTER ? javafx.print.Paper.NA_LETTER : javafx.print.Paper.A4,
                landscape ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM);

        double pixelsPerMm = DPI / 25.4;
        BufferedImage sheet = new BufferedImage(
                (int) Math.round(widthMm * pixelsPerMm),
                (int) Math.round(heightMm * pixelsPerMm),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            drawing.draw(new Java2dPainter(graphics, pixelsPerMm, images));
        } finally {
            graphics.dispose();
        }
        ImageView view = new ImageView(FxImages.of(sheet));
        view.setFitWidth(widthMm * POINTS_PER_MM);
        view.setFitHeight(heightMm * POINTS_PER_MM);
        view.setSmooth(true);
        // JavaFX places the node at the printable area's corner; move it back to the paper's corner.
        view.setTranslateX(-layout.getLeftMargin());
        view.setTranslateY(-layout.getTopMargin());
        boolean printed = job.printPage(layout, new Group(view));
        if (printed) {
            job.endJob();
        } else {
            job.cancelJob();
        }
        return printed;
    }
}
