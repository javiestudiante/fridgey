package ule.jescuj00.fridgey.ui.screens.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import ule.jescuj00.fridgey.R
import ule.jescuj00.fridgey.ui.theme.FridgeyShapes
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.Mint
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

/**
 * Editorial-kitchen login screen.
 *
 * The original screen used a Material `Button` filled with the primary
 * colour; the new design canvases Google's sign-in CTA on a white card
 * over the cream background, with a serif wordmark and an italic tagline
 * above it. Functional plumbing (state, error snackbar, navigation
 * callback) is unchanged — this is a visual rewrite only.
 *
 * NOTE: Apple Sign-In is intentionally NOT exposed on Android. Apple's
 * "you must offer Sign in with Apple" rule only applies to iOS apps that
 * use third-party SSO; Android has no such requirement.
 */
@Composable
fun LoginScreen(
    onSignedIn: (String) -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Credential Manager needs an Activity context. In a Compose host
    // mounted in MainActivity this is never null at runtime.
    val activity = LocalActivity.current
    val spacing = LocalFridgeySpacing.current

    LaunchedEffect(state.signedInUserId) {
        state.signedInUserId?.let { uid -> onSignedIn(uid) }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.xl),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // --- Branding -------------------------------------------------
                Text(
                    text = "Fridgey.",
                    style = MaterialTheme.typography.displayLarge,
                    color = Ink,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "menos basura, más cena.",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MintDeep,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))

                // --- SSO --------------------------------------------------------
                GoogleSignInButton(
                    onClick = { activity?.let(viewModel::onGoogleSignInClicked) },
                    enabled = !state.isLoading && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.weight(1f))

                // --- Disclaimer ------------------------------------------------
                Text(
                    text = "Al continuar aceptas nuestros términos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.xl),
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Mint,
                )
            }
        }
    }
}

/**
 * White CTA card with a subtle outline and a "Continuar con Google" label,
 * preceded by the official Google "G" mark (`res/drawable/ic_google.xml`).
 *
 * `tint = Color.Unspecified` is load-bearing: without it Compose would
 * recolour every path in the vector drawable with `LocalContentColor`,
 * collapsing the four-colour Google logo into a single tinted blob.
 */
@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalFridgeySpacing.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = FridgeyShapes.small,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(spacing.md))
            Text(
                text = "Continuar con Google",
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
            )
        }
    }
}
